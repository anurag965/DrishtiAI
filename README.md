# DrishtiAI

DrishtiAI is a cutting-edge Android application that brings multimodal vision-language AI capabilities directly to your mobile device. Built with privacy-first design, it enables on-device processing of text, images, and videos using advanced vision-language models (VLMs) without requiring internet connectivity.

## 🚀 Features

- **Multimodal AI Chat**: Engage in conversations with AI that understands both text and visual content
- **Image & Video Captioning**: Automatically generate descriptions for images and videos using VLMs
- **Live Video Analysis**: Real-time camera integration for instant vision tasks and analysis
- **Privacy-First Design**: All processing happens locally on your device - no data leaves your phone
- **On-Device Inference**: Powered by llama.cpp for efficient, offline AI processing
- **Vector Database**: SmolVectorDB for intelligent storage and retrieval of embeddings
- **Model Management**: Seamless downloading and management of AI models via Hugging Face Hub
- **Modern UI**: Sleek Material 3 interface built with Jetpack Compose

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Build System**: Gradle (Kotlin DSL)
- **AI Engine**: llama.cpp (C++) for optimized inference
- **Vision Processing**: Custom computer vision pipeline
- **Database**: SmolVectorDB for vector embeddings
- **Model Hub**: Hugging Face API integration
- **Architecture**: MVVM with dependency injection (Koin)

## 📋 Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- CMake 3.10+ (for native builds)
- Git

## 🏗 Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/anurag965/DrishtiAI.git
   cd DrishtiAI
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory and select it

3. **Build the project**
   - Wait for Gradle sync to complete
   - Build → Make Project (Ctrl+F9)

4. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Run → Run 'app' (Shift+F10)

## 📖 Usage

1. **Launch the App**: Open DrishtiAI on your Android device
2. **Download Models**: If no models are available, the app will guide you to download compatible VLMs
3. **Start Chatting**: Use the chat interface for text-based conversations with AI
4. **Vision Tasks**: Access camera features for image/video analysis and captioning
5. **Live Analysis**: Enable camera permissions for real-time vision processing

## 🏛 Project Structure

```
DrishtiAI/
├── app/                          # Main Android application
│   ├── src/main/java/com/example/drishtiai/
│   │   ├── MainActivity.kt       # App entry point and model loading
│   │   ├── VideoCaptionActivity.kt # Image/video captioning interface
│   │   ├── ui/screens/           # UI screens (chat, model download, etc.)
│   │   └── llm/                  # Language model management
├── hf-model-hub-api/             # Hugging Face model integration
├── smollm/                       # Small Language Model implementation
├── smolvectordb/                 # Vector database for embeddings
├── llama.cpp/                    # Core AI inference library
├── build.gradle.kts              # Root build configuration
└── settings.gradle.kts           # Project settings
```

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) for the optimized inference engine
- [Hugging Face](https://huggingface.co) for the model ecosystem
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for modern Android UI
- The open-source AI community for advancing multimodal models

## 📞 Support

If you have any questions or issues, please open an issue on GitHub or contact the maintainers.
