import re
with open("app/src/main/java/com/example/ui/DocumentViewModel.kt", "r") as f:
    code = f.read()

code = code.replace("onComplete: () -> Unit", "onComplete: (com.example.data.DocumentEntity) -> Unit")
code = code.replace("onComplete()", "onComplete(doc)")

with open("app/src/main/java/com/example/ui/DocumentViewModel.kt", "w") as f:
    f.write(code)
