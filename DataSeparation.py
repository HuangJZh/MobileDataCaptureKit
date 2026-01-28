import csv
from datetime import datetime

def process_sensor_data(input_file):
    # 定义存储各传感器数据的字典
    sensors = {
        'GYRO': {'data': [], 'timestamps': []},
        'ACC':  {'data': [], 'timestamps': []},
        'MAG':  {'data': [], 'timestamps': []},
        'PRESS': {'data': [], 'timestamps': []}
    }

    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            reader = csv.reader(f)
            
            # 读取所有行
            for row in reader:
                # 跳过空行或长度不足的行
                if len(row) < 2:
                    continue

                timestamp_str = row[0].strip()
                sensor_type = row[1].strip()

                # 跳过标题行（如果第一列无法解析为时间，则认为是标题）
                # 尝试解析时间格式 HH:MM:SS.mmm
                try:
                    # 注意：如果您的数据包含日期（如 2026-01-27），请相应调整格式
                    # 这里根据您提供的数据格式 '22:20:19.189' 进行解析
                    dt = datetime.strptime(timestamp_str, '%H:%M:%S.%f')
                except ValueError:
                    # 如果解析失败（比如是标题行 "Timestamp"），则跳过
                    continue

                # 确保行中有足够的数据 (至少要有到索引4的数据)
                if len(row) <= 4:
                    continue

                if sensor_type in sensors:
                    # 记录时间戳用于计算频率
                    sensors[sensor_type]['timestamps'].append(dt)
                    
                    if sensor_type == 'PRESS':
                        # 气压计数据在索引 4 (格式: Time, PRESS, , , Value)
                        value = row[4].strip()
                        sensors['PRESS']['data'].append([timestamp_str, value])
                    else:
                        # IMU 数据在索引 2, 3, 4 (格式: Time, Type, X, Y, Z)
                        x = row[2].strip()
                        y = row[3].strip()
                        z = row[4].strip()
                        sensors[sensor_type]['data'].append([timestamp_str, x, y, z])

    except FileNotFoundError:
        print(f"错误: 找不到文件 '{input_file}'，请确保文件在当前目录下。")
        return

    # --- 输出统计信息并在终端打印 ---
    print("-" * 65)
    print(f"{'Sensor':<10} | {'Count':<10} | {'Duration(s)':<12} | {'Freq(Hz)':<10}")
    print("-" * 65)

    for s_type, content in sensors.items():
        data_list = content['data']
        timestamps = content['timestamps']
        count = len(timestamps)
        
        # 计算频率
        freq = 0.0
        duration = 0.0
        
        if count > 1:
            # 计算首尾时间差
            # 注意：如果是跨天的数据只只有时间没有日期，计算可能会出现负数，
            # 但针对单次采集通常没问题。
            duration = (timestamps[-1] - timestamps[0]).total_seconds()
            if duration > 0:
                freq = (count - 1) / duration
        
        print(f"{s_type:<10} | {count:<10} | {duration:<12.3f} | {freq:<10.2f}")

        # 如果没有数据，不创建文件
        if count == 0:
            continue

        # --- 写入单独的文件 ---
        output_filename = f"{s_type}.csv"
        with open(output_filename, 'w', newline='', encoding='utf-8') as out_f:
            writer = csv.writer(out_f)
            
            # 根据类型写入不同的表头
            if s_type == 'PRESS':
                writer.writerow(['Timestamp', 'Value'])
            else:
                writer.writerow(['Timestamp', 'X', 'Y', 'Z'])
            
            writer.writerows(data_list)

    print("-" * 65)
    print("处理完成！已生成对应的 CSV 文件。")

if __name__ == "__main__":
    # 请确保这里的文件名与您保存的文件名一致
    # 您可以将您的数据保存为 data.csv 或者修改下面的文件名
    input_filename = "SENS_20260127_222010.csv" 
    process_sensor_data(input_filename)