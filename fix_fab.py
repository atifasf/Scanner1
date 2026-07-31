import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

# Add import
if "import androidx.compose.foundation.interaction.collectIsPressedAsState" not in code:
    code = code.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.foundation.interaction.collectIsPressedAsState")

fab_pattern = re.compile(r'floatingActionButton = \{(.*?)\}\n    \) \{ padding ->', re.DOTALL)

new_fab = """floatingActionButton = {
            if (currentTab == "Home") {
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val yOffset by androidx.compose.animation.core.animateDpAsState(if (isPressed) 6.dp else 0.dp)

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                                if (activity != null) {
                                    ScannerHelper.startScan(activity, scannerLauncher)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Shadow / Base for 3D effect
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 6.dp)
                            .background(Color(0xFF0D47A1), RoundedCornerShape(24.dp))
                    )
                    // Top Surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = yOffset)
                            .shadow(if (isPressed) 0.dp else 4.dp, RoundedCornerShape(24.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFF42A5F5), Color(0xFF1565C0))
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Scan",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->"""

code = fab_pattern.sub(new_fab, code)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(code)

