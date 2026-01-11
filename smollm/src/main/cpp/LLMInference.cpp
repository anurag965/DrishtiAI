#include "LLMInference.h"
#include <android/log.h>
#include <string>
#include <cstring>
#include <iostream>
#include <fstream>
#include <algorithm>
#include <vector>
#include "mtmd-helper.h"

#define TAG "[SmolLMAndroid-Cpp]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

void
LLMInference::loadModel(const char *model_path, float minP, float temperature, bool storeChats, long contextSize,
                        const char *chatTemplate, int nThreads, bool useMmap, bool useMlock) {
    LOGi("loading model with"
         "\n\tmodel_path = %s"
         "\n\tminP = %f"
         "\n\ttemperature = %f"
         "\n\tstoreChats = %d"
         "\n\tcontextSize = %li"
         "\n\tchatTemplate = %s"
         "\n\tnThreads = %d"
         "\n\tuseMmap = %d"
         "\n\tuseMlock = %d",
         model_path, minP, temperature, storeChats, contextSize, chatTemplate, nThreads, useMmap, useMlock);

    ggml_backend_load_all();

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;
    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) {
        LOGe("failed to load model from %s", model_path);
        throw std::runtime_error("loadModel() failed");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t)contextSize;
    ctx_params.n_batch = (uint32_t)contextSize;
    ctx_params.n_threads = nThreads;
    ctx_params.no_perf = true;
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        LOGe("llama_init_from_model() returned null)");
        throw std::runtime_error("llama_init_from_model() failed");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    _sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_sampler, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();

    if (chatTemplate == nullptr) {
        _chatTemplate = llama_model_chat_template(_model, nullptr);
    } else {
        _chatTemplate = strdup(chatTemplate);
    }
    this->_storeChats = storeChats;
}

void
LLMInference::addChatMessage(const char *message, const char *role) {
    _messages.push_back({strdup(role), strdup(message)});
}

float
LLMInference::getResponseGenerationTime() const {
    return (float) _responseNumTokens / (_responseGenerationTime / 1e6);
}

int
LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

void
LLMInference::startCompletion(const char *query) {
    if (!_storeChats) {
        _prevLen = 0;
        for (auto & message : _messages) {
            free((void*)message.role);
            free((void*)message.content);
        }
        _messages.clear();
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    addChatMessage(query, "user");
    int newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                                           _formattedMessages.data(), (int32_t)_formattedMessages.size());
    if (newLen > (int) _formattedMessages.size()) {
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                                           _formattedMessages.data(), (int32_t)_formattedMessages.size());
    }
    if (newLen < 0) {
        throw std::runtime_error("llama_chat_apply_template() failed");
    }
    std::string prompt(_formattedMessages.begin() + _prevLen, _formattedMessages.begin() + newLen);
    _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);

    if (_batch) {
        llama_batch_free(*_batch);
        delete _batch;
    }
    _batch = new llama_batch(llama_batch_init((int32_t)_promptTokens.size(), 0, 1));
    for (size_t i = 0; i < _promptTokens.size(); ++i) {
        common_batch_add(*_batch, _promptTokens[i], (llama_pos)i, { 0 }, i == _promptTokens.size() - 1);
    }
}

bool
LLMInference::_isValidUtf8(const char *response) {
    if (!response) return true;
    const unsigned char *bytes = (const unsigned char *) response;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

std::string
LLMInference::completionLoop() {
    if (!_ctx || !_batch || !_sampler) return "[EOG]";
    uint32_t contextSize = llama_n_ctx(_ctx);
    
    auto start = ggml_time_us();
    if (_batch->n_tokens > 0) {
        int res = llama_decode(_ctx, *_batch);
        if (res < 0) {
            LOGe("llama_decode failed with code %d", res);
            throw std::runtime_error("llama_decode() failed");
        } else if (res > 0) {
            LOGi("llama_decode warning code %d (KV cache full?)", res);
            return "[EOG]";
        }
        _batch->n_tokens = 0; // Clear after successful decode
    }

    // Strictly sync position with the current state of the KV cache
    _nCtxUsed = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;

    if (_nCtxUsed >= (int)contextSize) {
        LOGe("Context size reached: %d", _nCtxUsed);
        return "[EOG]";
    }

    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    llama_sampler_accept(_sampler, _currToken); // Commit choice to sampler state

    if (llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken)) {
        addChatMessage(_response.c_str(), "assistant");
        _response.clear();
        return "[EOG]";
    }
    
    std::string piece = common_token_to_piece(_ctx, _currToken, true);
    // Sanitize piece to prevent UI flickering (remove Carriage Return)
    piece.erase(std::remove(piece.begin(), piece.end(), '\r'), piece.end());
    
    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens += 1;
    _cacheResponseTokens += piece;

    // Prepare batch for the NEXT iteration using the token we just generated at the current position
    common_batch_add(*_batch, _currToken, (llama_pos)_nCtxUsed, { 0 }, true);

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

void
LLMInference::stopCompletion() {
    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }
    _response.clear();
    if (!is_multimodal_model && _chatTemplate) {
        _prevLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), false, nullptr, 0);
        if (_prevLen < 0) {
            throw std::runtime_error("llama_chat_apply_template() failed");
        }
    }
}

LLMInference::~LLMInference() {
    for (llama_chat_message &message: _messages) {
        free((void*)message.role);
        free((void*)message.content);
    }
    if (_ctx) llama_free(_ctx);
    if (_model) llama_model_free(_model);
    if (_batch) {
        llama_batch_free(*_batch);
        delete _batch;
    }
    if (_sampler) llama_sampler_free(_sampler);
    if (_mtmd_ctx) mtmd_free(_mtmd_ctx);
}

// ========== MULTIMODAL IMPLEMENTATION ==========

bool LLMInference::loadMultimodalModel(const char* model_path, const char* mmproj_path_arg, float minP, float temperature, int n_gpu_layers, long contextSize) {
    LOGi("loadMultimodalModel: model = %s, mmproj = %s, minP = %f, temp = %f", model_path, mmproj_path_arg, minP, temperature);
    mmproj_path = mmproj_path_arg;
    ggml_backend_load_all();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;
    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) return false;

    mtmd_context_params mparams = mtmd_context_params_default();
    mparams.use_gpu = n_gpu_layers > 0;
    _mtmd_ctx = mtmd_init_from_file(mmproj_path.c_str(), _model, mparams);
    if (!_mtmd_ctx) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = (uint32_t)contextSize;
    ctx_params.n_batch   = (uint32_t)contextSize;
    ctx_params.n_threads = 4;
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) return false;

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    _sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_sampler, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();
    
    addChatMessage("You are a helpful assistant that describes images and scenes accurately. Look at the visual input provided before answering.", "system");
    
    _chatTemplate = llama_model_chat_template(_model, nullptr);
    _storeChats   = false;
    is_multimodal_model = true;
    return true;
}

void LLMInference::addVideoFrame(const uint8_t* pixel_data, int width, int height, int channels) {
    if (channels != 3) return;
    ImageFrame frame;
    frame.width    = width;
    frame.height   = height;
    frame.channels = channels;
    frame.data.assign(pixel_data, pixel_data + width * height * channels);
    videoFrames.push_back(frame);
}

bool LLMInference::buildMultimodalChat(const char* text_prompt) {
    if (!is_multimodal_model || !_ctx || videoFrames.empty()) return false;

    if (!_batch) {
        _batch = new llama_batch(llama_batch_init(1, 0, 1));
    }
    _batch->n_tokens = 0;

    // Reset KV cache for each new analysis
    llama_memory_clear(llama_get_memory(_ctx), true);
    llama_sampler_reset(_sampler);
    
    for (auto & message : _messages) {
        if (strcmp(message.role, "system") != 0) {
            free((void*)message.role);
            free((void*)message.content);
        }
    }
    _messages.erase(std::remove_if(_messages.begin(), _messages.end(), [](const llama_chat_message& m){
        return strcmp(m.role, "system") != 0;
    }), _messages.end());
    
    _response.clear();
    _cacheResponseTokens.clear();

    std::string markers = "";
    for (size_t i = 0; i < videoFrames.size(); ++i) {
        markers += mtmd_default_marker();
    }
    // Proper prompt construction
    std::string user_content = markers + "\n" + std::string(text_prompt);
    addChatMessage(user_content.c_str(), "user");

    int newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                                           _formattedMessages.data(), (int32_t)_formattedMessages.size());
    if (newLen > (int) _formattedMessages.size()) {
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true,
                                           _formattedMessages.data(), (int32_t)_formattedMessages.size());
    }
    if (newLen < 0) return false;

    std::string full_prompt(_formattedMessages.begin(), _formattedMessages.begin() + newLen);
    LOGi("Multimodal prompt: %s", full_prompt.c_str());

    std::vector<const mtmd_bitmap*> bitmaps;
    for (const auto& frame : videoFrames) {
        bitmaps.push_back(mtmd_bitmap_init(frame.width, frame.height, frame.data.data()));
    }

    mtmd_input_text text;
    text.text          = full_prompt.c_str();
    text.add_special   = false; 
    text.parse_special = true;

    mtmd_input_chunks* chunks_ptr = mtmd_input_chunks_init();
    if (!chunks_ptr) {
        for (const auto& bitmap : bitmaps) mtmd_bitmap_free((mtmd_bitmap*)bitmap);
        return false;
    }
    mtmd::input_chunks chunks(chunks_ptr);

    if (mtmd_tokenize(_mtmd_ctx, chunks.ptr.get(), &text, bitmaps.data(), bitmaps.size()) != 0) {
        for (const auto& bitmap : bitmaps) mtmd_bitmap_free((mtmd_bitmap*)bitmap);
        return false;
    }

    for (const auto& bitmap : bitmaps) mtmd_bitmap_free((mtmd_bitmap*)bitmap);

    llama_pos n_past = 0;
    if (mtmd_helper_eval_chunks(_mtmd_ctx, _ctx, chunks.ptr.get(), 0, 0, (int32_t)llama_n_batch(_ctx), true, &n_past)) {
        LOGe("mtmd_helper_eval_chunks failed");
        return false;
    }

    // Synchronize current position with evaluated multimodal chunks
    _nCtxUsed = (int)n_past;
 
    return true;
}

void LLMInference::clearVideoFrames() { videoFrames.clear(); }
int LLMInference::getFrameCount() const { return (int) videoFrames.size(); }
