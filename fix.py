import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

replacement = """  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {"""

content = content.replace('  signingConfigs {\n    create("debugConfig") {', replacement)
content = content.replace('versionCode = 7\n    versionName = "7.0"', 'versionCode = 8\n    versionName = "8.0"')
content = content.replace('      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")\n    }', '      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")\n      signingConfig = signingConfigs.getByName("release")\n    }')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
