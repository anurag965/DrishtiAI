# DrishtiAI

DrishtiAI is an innovative Android application that brings the power of large language models (LLMs) directly to your device. Built with Kotlin and Jetpack Compose, it leverages the llama.cpp library for efficient, on-device AI inference, enabling privacy-focused and offline-capable AI interactions.

## 🚀 Features

- **On-Device AI**: Run LLMs locally without internet dependency
- **Privacy-First**: All processing happens on your device
- **Modern UI**: Sleek interface built with Jetpack Compose and Material 3
- **Vector Database Integration**: Efficient storage and retrieval with SmolVectorDB
- **Hugging Face Integration**: Easy model downloading via HF Model Hub API
- **Small Language Models**: Optimized for resource-constrained devices with SmolLM
- **Cross-Platform Core**: Powered by llama.cpp for broad model support

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle (Kotlin DSL)
- **AI Engine**: llama.cpp (C++)
- **Database**: SmolVectorDB
- **Model Hub**: Hugging Face API
- **Architecture**: MVVM

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

1. Launch the DrishtiAI app on your Android device
2. Download or select a compatible LLM model
3. Start chatting with the AI locally on your device
4. Explore vector database features for enhanced interactions

## 🏛 Project Structure

```
DrishtiAI/
├── app/                    # Main Android application
├── hf-model-hub-api/       # Hugging Face model integration
├── smollm/                 # Small Language Model module
├── smolvectordb/           # Vector database implementation
├── llama.cpp/              # Core AI inference library
├── build.gradle.kts        # Root build configuration
└── settings.gradle.kts     # Project settings
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

- [llama.cpp](https://github.com/ggerganov/llama.cpp) for the core inference engine
- [Hugging Face](https://huggingface.co) for model hosting
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for modern Android UI

## 📞 Support

If you have any questions or issues, please open an issue on GitHub or contact the maintainers.
