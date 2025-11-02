import os

# যেসব .m3u ফাইল merge করবে (index.m3u বাদে)
files = [f for f in os.listdir() if f.endswith(".m3u") and f != "index.m3u"]

merged = "#EXTM3U\n\n"

for file in files:
    with open(file, "r", encoding="utf-8") as f:
        lines = f.readlines()
        # প্রথম লাইন যদি #EXTM3U হয়, সেটা সরিয়ে দেই
        if lines and lines[0].startswith("#EXTM3U"):
            lines = lines[1:]
        merged += "".join(lines) + "\n"

with open("index.m3u", "w", encoding="utf-8") as f:
    f.write(merged)

print("✅ index.m3u সফলভাবে তৈরি হয়েছে!")
