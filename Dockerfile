FROM eclipse-temurin:17-jdk-jammy AS builder
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline.zip && \
    unzip -q /tmp/cmdline.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline.zip && \
    yes | sdkmanager --licenses && \
    sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
FROM scratch AS artifact
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /app-debug.apk
