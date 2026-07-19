import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Enhance main action cards (ID Card, Extract Text, etc.)
# Find Card(onClick = { ... }, modifier = ..., colors = CardDefaults.cardColors(containerColor = ...copy(alpha = 0.5f)), shape = ...)
code = re.sub(
    r'colors = CardDefaults.cardColors\(\s*containerColor = MaterialTheme.colorScheme.([^.]+)\.copy\(alpha = 0\.5f\)\s*\)',
    r'colors = CardDefaults.cardColors(\n                                containerColor = MaterialTheme.colorScheme.\1\n                            ),\n                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)',
    code
)

# Also enhance FloatingActionButton
code = code.replace("FloatingActionButton(\n                    onClick = {", 
                    "FloatingActionButton(\n                    onClick = {")
code = re.sub(
    r'FloatingActionButton\(\s*onClick =',
    r'FloatingActionButton(\n                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 4.dp),\n                    onClick =',
    code
)

# Convert TextButton and OutlinedButton to Button with elevations, but keep some distinction
# TextButton -> Button (filledTonal)
# OutlinedButton -> Button (filled)

def replace_text_button(match):
    prefix = match.group(1)
    args = match.group(2)
    # If colors or elevation already in args, just return
    if "colors =" in args or "elevation =" in args:
        return match.group(0)
    
    return f"{prefix}Button({args}, colors = ButtonDefaults.filledTonalButtonColors(), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)"

code = re.sub(r'([ \t]*)TextButton\((.*?)(?=\s*\))', replace_text_button, code, flags=re.DOTALL)

def replace_outlined_button(match):
    prefix = match.group(1)
    args = match.group(2)
    if "colors =" in args or "elevation =" in args:
        return match.group(0)
    
    return f"{prefix}Button({args}, elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)"

code = re.sub(r'([ \t]*)OutlinedButton\((.*?)(?=\s*\))', replace_outlined_button, code, flags=re.DOTALL)

# Add elevation to regular Buttons that don't have it
def replace_button(match):
    prefix = match.group(1)
    args = match.group(2)
    if "elevation =" in args or "colors =" in args:
        return match.group(0)
    return f"{prefix}Button({args}, elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)"

# We need to only match exact Button( not IconButton or RadioButton
code = re.sub(r'([ \t]*)(?<!\w)Button\((.*?)(?=\s*\))', replace_button, code, flags=re.DOTALL)

# Let's write the code back
with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(code)

