import xml.etree.ElementTree as ET
import sys
import os

def restore_strings(old_file, new_file):
    if not os.path.exists(old_file) or not os.path.exists(new_file):
        print(f"File not found: {old_file} or {new_file}")
        return

    # Use a simpler parser that handles comments and preserves some structure
    def get_strings(file_path):
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            return {child.get('name'): child for child in root if child.tag == 'string'}
        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            return {}

    old_strings = get_strings(old_file)
    new_strings = get_strings(new_file)

    missing_names = set(old_strings.keys()) - set(new_strings.keys())

    if not missing_names:
        print(f"No strings missing in {new_file}")
        return

    # Read the current file to append at the end correctly
    with open(new_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Find the closing resources tag
    closing_tag_index = -1
    for i in range(len(lines) - 1, -1, -1):
        if '</resources>' in lines[i]:
            closing_tag_index = i
            break

    if closing_tag_index == -1:
        print(f"Could not find closing tag in {new_file}")
        return

    new_content = []
    for name in sorted(missing_names):
        element = old_strings[name]
        xml_str = ET.tostring(element, encoding='unicode').strip()
        new_content.append(f"    {xml_str}\n")

    lines.insert(closing_tag_index, "".join(new_content))

    with open(new_file, 'w', encoding='utf-8') as f:
        f.writelines(lines)

    print(f"Restored {len(missing_names)} strings to {new_file}")

restore_strings('strings_old.xml', 'app/src/main/res/values/strings.xml')
restore_strings('strings_zh_old.xml', 'app/src/main/res/values-zh-rCN/strings.xml')
