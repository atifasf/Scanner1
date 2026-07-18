import re

with open("app/src/main/java/com/example/ui/DocumentViewModel.kt", "r") as f:
    code = f.read()

# Replace viewModelScope.launch { inside convertPdfToWord
start_idx = code.find("fun convertPdfToWord(")
end_idx = code.find("fun extractTextFromUri", start_idx)
if end_idx == -1: end_idx = len(code)

segment = code[start_idx:end_idx]

# Replace the launch
segment = segment.replace("viewModelScope.launch {", "viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {")

# Wrap callbacks
segment = segment.replace("onStart()", "kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onStart() }")
segment = segment.replace('onProgress("', 'kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onProgress("')
segment = segment.replace(')...")', ')...") }')
segment = segment.replace('ing...")', 'ing...") }')
segment = segment.replace('ocument...")', 'ocument...") }')

segment = segment.replace('onFailure(', 'kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure(')
segment = segment.replace(' Make sure it\'s a valid local PDF.")', ' Make sure it\'s a valid local PDF.") }')
segment = segment.replace(' corrupted or password-protected.")', ' corrupted or password-protected.") }')
segment = segment.replace('The PDF has 0 pages.")', 'The PDF has 0 pages.") }')
segment = segment.replace('Unknown error occurred")', 'Unknown error occurred") }')

segment = segment.replace('onSuccess(documentEntity.id)', 'kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onSuccess(documentEntity.id) }')

# Fix the JPEG quality
segment = segment.replace('bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)', 'bitmap.compress(Bitmap.CompressFormat.JPEG, 65, out)')

code = code[:start_idx] + segment + code[end_idx:]

with open("app/src/main/java/com/example/ui/DocumentViewModel.kt", "w") as f:
    f.write(code)
