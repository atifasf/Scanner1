import sys

path = 'app/src/main/java/com/example/ui/DocumentViewModel.kt'
with open(path, 'r') as f:
    content = f.read()

# 1. Update Gemini Prompt
old_prompt_en = '"Extract all text from this image, preserving the original structure, paragraphs, headings, and lists. If there are tables in the image, represent them EXACTLY using Markdown table format (with | columns and |---| headers). Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'
new_prompt_en = '''"Extract all content from the input image accurately. Formatting Rules:\n" +
"1. Preserve layout structure as closely as possible using HTML tags.\n" +
"2. Format regular text inside paragraph tags <p>...</p>.\n" +
"3. CRITICAL: Whenever you detect a table, grid, or column-based data, convert it into a well-structured HTML table using <table>, <thead>, <tbody>, <tr>, <th>, and <td> tags.\n" +
"4. Maintain row and column counts exactly as they appear in the source image.\n" +
"5. Do not lose any textual content inside table cells.\n" +
"6. Output ONLY the raw HTML string without any markdown code block wrappers (like ```html)."'''

old_prompt_other = '"Extract all $langName text from this image, preserving the original structure, paragraphs, headings, and lists. If there are tables in the image, represent them EXACTLY using Markdown table format (with | columns and |---| headers). Do not translate. Output only the exact words found in the image. Ensure the output is well-formatted for a text document. Do not include any commentary, explanations, preamble, or markdown code blocks (like ```)."'
new_prompt_other = '''"Extract all $langName content from the input image accurately. Formatting Rules:\n" +
"1. Preserve layout structure as closely as possible using HTML tags.\n" +
"2. Format regular text inside paragraph tags <p>...</p>.\n" +
"3. CRITICAL: Whenever you detect a table, grid, or column-based data, convert it into a well-structured HTML table using <table>, <thead>, <tbody>, <tr>, <th>, and <td> tags.\n" +
"4. Maintain row and column counts exactly as they appear in the source image.\n" +
"5. Do not lose any textual content inside table cells.\n" +
"6. Output ONLY the raw HTML string without any markdown code block wrappers (like ```html)."'''

content = content.replace(old_prompt_en, new_prompt_en)
content = content.replace(old_prompt_other, new_prompt_other)

with open(path, 'w') as f:
    f.write(content)

print("Prompt updated")
