import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Replace the 4 card colors with dark blue
# Card 1: ID Card
code = re.sub(
    r'containerColor = MaterialTheme\.colorScheme\.primaryContainer',
    r'containerColor = Color(0xFF0D47A1), contentColor = Color.White',
    code
)
# Card 2: Extract Text
code = re.sub(
    r'containerColor = MaterialTheme\.colorScheme\.secondaryContainer',
    r'containerColor = Color(0xFF0D47A1), contentColor = Color.White',
    code
)
# Card 3: PDF to Word
code = re.sub(
    r'containerColor = MaterialTheme\.colorScheme\.tertiaryContainer',
    r'containerColor = Color(0xFF0D47A1), contentColor = Color.White',
    code
)
# Card 4: Scan Table to Excel
code = re.sub(
    r'containerColor = MaterialTheme\.colorScheme\.surfaceVariant',
    r'containerColor = Color(0xFF0D47A1), contentColor = Color.White',
    code
)

# And fix tint / color for the icons and text inside these cards.
# ID Card
code = code.replace(
    'tint = MaterialTheme.colorScheme.primary',
    'tint = Color.White'
)
code = code.replace(
    'color = MaterialTheme.colorScheme.onPrimaryContainer',
    'color = Color.White'
)
# Extract Text
code = code.replace(
    'tint = MaterialTheme.colorScheme.secondary',
    'tint = Color.White'
)
code = code.replace(
    'color = MaterialTheme.colorScheme.onSecondaryContainer',
    'color = Color.White'
)
# PDF to Word
code = code.replace(
    'tint = MaterialTheme.colorScheme.tertiary',
    'tint = Color.White'
)
code = code.replace(
    'color = MaterialTheme.colorScheme.onTertiaryContainer',
    'color = Color.White'
)
# Scan Table
code = code.replace(
    'tint = MaterialTheme.colorScheme.onSurfaceVariant',
    'tint = Color.White'
)
# Wait, Scan table might have a different text color
code = code.replace(
    'color = MaterialTheme.colorScheme.onSurfaceVariant',
    'color = Color.White'
)

# Let's ensure the Scan Table card has elevation
code = re.sub(
    r'colors = CardDefaults\.cardColors\(\s*containerColor = Color\(0xFF0D47A1\), contentColor = Color\.White,\s*\),\s*shape = RoundedCornerShape\(16\.dp\)',
    r'colors = CardDefaults.cardColors(\n                                containerColor = Color(0xFF0D47A1), contentColor = Color.White\n                            ),\n                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),\n                            shape = RoundedCornerShape(16.dp)',
    code
)

# Make all 4 cards have 8.dp default elevation
code = code.replace('elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)', 'elevation = CardDefaults.cardElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)')

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(code)
