# Contributing to Frida Manager

Thank you for your interest in contributing to Frida Manager! This document provides guidelines for contributing to the project.

## Code of Conduct

By participating in this project, you agree to abide by our code of conduct:
- Be respectful and inclusive
- Use welcoming and inclusive language
- Be collaborative and helpful
- Focus on what is best for the community

## How to Contribute

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- Clear description of the problem
- Steps to reproduce the issue
- Expected vs actual behavior
- Device information (Android version, architecture, root method)
- App version and build information
- Relevant logs or screenshots

### Suggesting Features

Feature suggestions are welcome! Please:
- Check existing issues for similar suggestions
- Provide clear use cases for the feature
- Explain how it would benefit users
- Consider implementation complexity

### Pull Requests

1. **Fork the repository**
2. **Create a feature branch** from `main`
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes** following the coding standards
4. **Test thoroughly** on different devices/architectures
5. **Update documentation** if needed
6. **Commit with clear messages**
   ```bash
   git commit -m "Add feature: description of what you added"
   ```
7. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```
8. **Create a Pull Request** with:
   - Clear title and description
   - Reference any related issues
   - Screenshots for UI changes
   - Testing notes

## Development Setup

### Prerequisites
- Android Studio Arctic Fox or newer
- Android SDK 34
- JDK 17+
- Rooted Android device for testing

### Setting up the project
```bash
git clone https://github.com/piyush2947/frida-server-manager.git
cd frida-server-manager
./gradlew build
```

### Code Style

- Follow Android coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small
- Use Jetpack Compose best practices

### Testing

- Test on multiple Android versions (7.0+)
- Test on different architectures (ARM64, ARM, x86, x86_64)
- Test both manual and automatic installation flows
- Verify root permissions handling
- Test error scenarios and edge cases

### Documentation

- Update README.md for user-facing changes
- Add inline comments for complex code
- Update app screenshots if UI changes
- Document new features in commit messages

## Security Considerations

Since this app deals with root access and system-level operations:
- Never commit sensitive information
- Follow security best practices
- Test permissions carefully
- Document security implications of changes
- Report security issues privately

## Release Process

1. Version bump in `build.gradle.kts`
2. Update `CHANGELOG.md`
3. Create release APK
4. Tag the release
5. Update GitHub release with APK and notes

## Getting Help

- Check existing [Issues](../../issues)
- Review [Discussions](../../discussions)
- Ask questions in new issues with "question" label

## Recognition

Contributors will be recognized in:
- README.md acknowledgments
- Release notes
- GitHub contributors list

Thank you for contributing to make Frida Manager better for everyone!