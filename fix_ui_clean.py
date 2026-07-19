import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Fix 1: fillMaxWidth(, elevation... -> fillMaxWidth()
code = code.replace(
    "Modifier.fillMaxWidth(, elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp))",
    "Modifier.fillMaxWidth()"
)

code = code.replace(
    "Modifier.fillMaxWidth(, colors = ButtonDefaults.filledTonalButtonColors(), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp))",
    "Modifier.fillMaxWidth()"
)

# Fix 2: isNotBlank(, colors... -> isNotBlank()
code = code.replace(
    "isNotBlank(, colors = ButtonDefaults.filledTonalButtonColors(), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp))",
    "isNotBlank()"
)

# Fix 3: isIdCardScan, elevation... -> isIdCardScan
code = code.replace(
    "isIdCardScan, elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)",
    "isIdCardScan"
)

# Fix 4: }, elevation = ButtonDefaults... -> }
code = code.replace(
    "}, elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)",
    "}"
)

# Other stray commas or syntax issues from my previous script?
# Let's check for any other , elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp) that is not immediately preceded by closing parens.
# Actually, wait. Did the replacements for TextButton that DID NOT have nested parens work?
# TextButton(onClick = { ... }) { ... } 
# Let's see what happened to TextButton(onClick = { ... }) 

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(code)

