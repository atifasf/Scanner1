#!/bin/bash
sed -i 's/val coroutineScope = rememberCoroutineScope()//g' app/src/main/java/com/example/ui/screens/HomeScreen.kt
sed -i 's/var signatureSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }/val coroutineScope = rememberCoroutineScope()\n    var signatureSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }/g' app/src/main/java/com/example/ui/screens/HomeScreen.kt
