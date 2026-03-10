#!/usr/bin/env python3
"""
提取液体纹理主色调（众数颜色）
输出格式与 Kotlin 的 0xAARRGGBB 一致
"""

from PIL import Image
import os
from collections import Counter

def extract_dominant_color(image_path, quantize_level=8):
    """提取图片主色调（众数颜色），忽略透明像素"""
    img = Image.open(image_path)
    
    # 转换为RGBA模式
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    pixels = list(img.getdata())
    
    # 过滤掉透明像素并量化颜色
    quantized_colors = []
    for r, g, b, a in pixels:
        if a < 128:  # 忽略半透明白色
            continue
        # 量化颜色（合并相近颜色）
        qr = (r // quantize_level) * quantize_level
        qg = (g // quantize_level) * quantize_level
        qb = (b // quantize_level) * quantize_level
        quantized_colors.append((qr, qg, qb))
    
    if not quantized_colors:
        return (0, 0, 0)
    
    # 找出出现次数最多的颜色
    color_counts = Counter(quantized_colors)
    dominant_color = color_counts.most_common(1)[0][0]
    
    return dominant_color

def color_to_kotlin_code(r, g, b):
    """转换为 Kotlin 的 0xAARRGGBB 格式"""
    return f"0xFF{r:02X}{g:02X}{b:02X}"

def process_all_liquids(liquid_dir):
    """处理目录下所有图片并生成 Kotlin 代码"""
    png_files = sorted([f for f in os.listdir(liquid_dir) if f.endswith('.png')])
    
    if not png_files:
        print(f"No PNG files found in: {liquid_dir}")
        return
    
    print("// 液体颜色定义 - 由 extract_colors.py 自动生成")
    print(f"// 处理文件数: {len(png_files)}")
    print()
    print("enum class Liquids(")
    print("    val id: String,")
    print("    val color: Int  // 0xAARRGGBB 格式")
    print(") {")
    print()
    
    color_map = {}
    
    for filename in png_files:
        filepath = os.path.join(liquid_dir, filename)
        name = os.path.splitext(filename)[0].upper()
        
        r, g, b = extract_dominant_color(filepath)
        color_code = color_to_kotlin_code(r, g, b)
        color_map[name] = color_code
        
        print(f"    {name}(\"{name.lower()}\", {color_code}.toInt()),")
    
    print("    ;")
    print()
    print("    companion object {")
    print("        val ALL = entries.toList()")
    print("    }")
    print()
    print("    val displayName: String")
    print('        get() = id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }')
    print("}")
    print()
    print("// 颜色摘要:")
    for name, color in color_map.items():
        print(f"// {name}: {color}")

# 主入口
liquid_textures_dir = r"D:\Projects\Java\mindustry\src\main\resources\assets\mindustry\textures\item\liquid"

process_all_liquids(liquid_textures_dir)