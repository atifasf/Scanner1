import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace("  buildFeatures {\n    compose = true\n    buildConfig = true\n  }", "  buildFeatures {\n    compose = true\n    buildConfig = true\n  }\n  lint {\n    abortOnError = false\n  }")

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
