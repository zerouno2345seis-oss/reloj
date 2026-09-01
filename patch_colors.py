import re

with open('app/src/main/java/com/quran/watch8/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

# Replace all occurrences of "color = Color.White" with "color = fontColor" but ONLY inside SmartWatchFaceTile
# SmartWatchFaceTile starts at line 465
start_idx = content.find("private fun SmartWatchFaceTile")
end_idx = content.find("private fun QuickTileEditorDialog", start_idx)

if start_idx != -1 and end_idx != -1:
    section = content[start_idx:end_idx]
    # Keep lines that we know we don't want to replace, like:
    # "Text("🔄", fontSize = 6.sp, color = Color.White)"
    # "Text("📁", fontSize = 7.sp, color = Color.White)"
    # "parseHexColor(slot.fontColorHex, Color.White)"
    
    # Simple strategy: Replace all color = Color.White with color = fontColor
    # Then revert the ones we know we shouldn't touch
    section = section.replace("color = Color.White", "color = fontColor")
    section = section.replace("parseHexColor(slot.fontColorHex, fontColor)", "parseHexColor(slot.fontColorHex, Color.White)")
    section = section.replace("Text(\"🔄\", fontSize = 6.sp, color = fontColor)", "Text(\"🔄\", fontSize = 6.sp, color = Color.White)")
    section = section.replace("Text(\"📁\", fontSize = 7.sp, color = fontColor)", "Text(\"📁\", fontSize = 7.sp, color = Color.White)")
    
    new_content = content[:start_idx] + section + content[end_idx:]
    with open('app/src/main/java/com/quran/watch8/ui/screens/HomeScreen.kt', 'w') as f:
        f.write(new_content)
    print("Patched colors in SmartWatchFaceTile")
else:
    print("Could not find section")
