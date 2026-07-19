import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Replace BottomNavigationBar and BottomNavItem
# Find where it starts and ends
start_idx = code.find("@Composable\nfun BottomNavigationBar")

end_idx = code.find("}", code.rfind("fun BottomNavItem")) 
# This might not be robust. Let's just find the exact text using regex.

replacement = """@Composable
fun BottomNavigationBar(currentTab: String, onTabSelected: (String) -> Unit, onNavigateToSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF1976D2), Color(0xFF0D47A1))
                )
            )
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(icon = Icons.Default.Home, label = "Home", isSelected = currentTab == "Home", onClick = { onTabSelected("Home") })
        BottomNavItem(icon = Icons.Default.TextFields, label = "OCR", isSelected = currentTab == "OCR", onClick = { onTabSelected("OCR") })
        BottomNavItem(icon = Icons.Default.Build, label = "Tools", isSelected = currentTab == "Tools", onClick = { onTabSelected("Tools") })
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            isSelected = false,
            onClick = onNavigateToSettings
        )
    }
}

@Composable
fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
    val selectedColor = Color(0xFFFFC107) // Gold/Amber
    val unselectedColor = Color.White.copy(alpha = 0.7f)
    val currentColor = if (isSelected) selectedColor else unselectedColor

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .background(Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x60FFFFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (label == "OCR") {
                    Text(
                        text = "OCR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentColor
                    )
                } else {
                    Icon(icon, contentDescription = label, tint = currentColor, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (label == "OCR") {
                    Text(
                        text = "OCR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentColor
                    )
                } else {
                    Icon(icon, contentDescription = label, tint = currentColor, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = currentColor
        )
    }
}"""

# Using regex to replace the old implementations
import re
pattern = re.compile(r'@Composable\nfun BottomNavigationBar.*?fun BottomNavItem.*?\}\n\}', re.DOTALL)
new_code = pattern.sub(replacement, code)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(new_code)
