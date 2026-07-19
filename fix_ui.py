with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Fix modifier = Modifier.fillMaxWidth(, elevation...
code = code.replace("Modifier.fillMaxWidth(, elevation", "Modifier.fillMaxWidth(), elevation")
code = code.replace("Modifier.fillMaxWidth(, colors", "Modifier.fillMaxWidth(), colors")

# Fix if (newDocumentName.isNotBlank(, colors...
code = code.replace(
    "Button(onClick = {\n                    if (newDocumentName.isNotBlank(, colors = ButtonDefaults.filledTonalButtonColors(), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp))) {",
    "Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {\n"
)
# Add colors to the Button correctly
code = code.replace(
    "confirmButton = {\n                Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {",
    "confirmButton = {\n                Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {",
) # Wait, need to fix the Button arguments!
# Actually, let's fix it this way:
code = code.replace(
    "                Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {",
    "                Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {", # wait, I will fix it below
)

code = code.replace(
    "Button(onClick = {\n                    if (newDocumentName.isNotBlank(, colors = ButtonDefaults.filledTonalButtonColors(), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp))) {",
    "Button(onClick = {\n                    if (newDocumentName.isNotBlank()) {",
)
# Wait, I need to add the elevation to that button:
code = code.replace(
    "confirmButton = {\n                Button(onClick = {",
    "confirmButton = {\n                Button(onClick = {",
) # we can just let it be Button(onClick = { ... })

# Let's see line 202:
# 202:47 Syntax error: Expecting an argument.
# 202:49 No parameter with name 'colors' found.
# Let's check line 201-203.
