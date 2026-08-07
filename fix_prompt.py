import os
path = '/app/applet/app/src/main/java/com/example/ui/DocumentViewModel.kt'
with open(path, 'r') as f:
    content = f.read()

old_en = '"Extract all text from this image, preserving the original structure, paragraphs, headings, and lists. Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'
new_en = '"Extract all text from this image, preserving the original structure, paragraphs, headings, and lists. If there are tables in the image, represent them EXACTLY using Markdown table format (with | columns and |---| headers). Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'

old_other = '"Extract all $langName text from this image, preserving the original structure, paragraphs, headings, and lists. Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'
new_other = '"Extract all $langName text from this image, preserving the original structure, paragraphs, headings, and lists. If there are tables in the image, represent them EXACTLY using Markdown table format (with | columns and |---| headers). Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'

content = content.replace(old_en, new_en)
content = content.replace(old_other, new_other)

with open(path, 'w') as f:
    f.write(content)
