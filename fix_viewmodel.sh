sed -i 's/viewModelScope.launch {/viewModelScope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {/g' app/src/main/java/com/example/ui/DocumentViewModel.kt
