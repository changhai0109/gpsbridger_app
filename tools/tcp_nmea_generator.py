import socket
import threading
import time
import math

# Server config
HOST = "0.0.0.0"
PORT = 5000

# Circular motion config
lat0 = 37.0       # center latitude
lon0 = -122.0     # center longitude
R = 1000         # 10 km radius in meters
v = 20             # 2 m/s speed

circumference = 2 * math.pi * R
T = circumference / v  # seconds to complete circle
omega = 2 * math.pi / T
start_time = time.time()

def format_lat_lon(lat, lon):
    """Convert decimal degrees to NMEA ddmm.mmmm format"""
    lat_d = int(abs(lat))
    lat_m = (abs(lat) - lat_d) * 60
    lat_dir = "N" if lat >= 0 else "S"

    lon_d = int(abs(lon))
    lon_m = (abs(lon) - lon_d) * 60
    lon_dir = "E" if lon >= 0 else "W"

    lat_str = f"{lat_d:02d}{lat_m:07.4f}"
    lon_str = f"{lon_d:03d}{lon_m:07.4f}"
    return lat_str, lat_dir, lon_str, lon_dir

def nmea_checksum(sentence_body):
    cksum = 0
    for c in sentence_body:
        cksum ^= ord(c)
    return f"{cksum:02X}"

def generate_nmea_sentences():
    t = time.time() - start_time
    delta_lat = R / 111111
    delta_lon = R / (111111 * math.cos(math.radians(lat0)))

    lat = lat0 + delta_lat * math.sin(omega * t)
    lon = lon0 + delta_lon * math.cos(omega * t)

    lat_str, lat_dir, lon_str, lon_dir = format_lat_lon(lat, lon)

    hhmmss = time.strftime("%H%M%S", time.gmtime())
    date = time.strftime("%d%m%y", time.gmtime())
    speed_kmh = v * 3.6  # m/s -> km/h

    # Heading tangent to the circle
    heading_rad = math.atan2(
        omega * delta_lat * math.cos(omega * t),
        -omega * delta_lon * math.sin(omega * t)
    )
    track = (math.degrees(heading_rad) + 360) % 360

    # --- GPRMC ---
    gprmc_body = f"GPRMC,{hhmmss}.00,A,{lat_str},{lat_dir},{lon_str},{lon_dir},{speed_kmh:.1f},{track:.1f},{date},,,A"
    gprmc = f"${gprmc_body}*{nmea_checksum(gprmc_body)}"

    # --- GPGGA ---
    # Fix quality: 1 = GPS fix, Num satellites = 8, HDOP=0.9, Altitude=50m
    gpgga_body = f"GPGGA,{hhmmss}.00,{lat_str},{lat_dir},{lon_str},{lon_dir},1,08,0.9,50.0,M,0.0,M,,"
    gpgga = f"${gpgga_body}*{nmea_checksum(gpgga_body)}"

    return gprmc, gpgga

def client_thread(conn, addr):
    print(f"Client connected: {addr}")
    try:
        while True:
            gprmc, gpgga = generate_nmea_sentences()
            conn.sendall((gprmc + "\r\n" + gpgga + "\r\n").encode("ascii"))
            time.sleep(1)
    except (BrokenPipeError, ConnectionResetError):
        print(f"Client disconnected: {addr}")
    finally:
        conn.close()

def start_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()
        print(f"Fake NMEA server listening on {HOST}:{PORT}")
        while True:
            conn, addr = s.accept()
            threading.Thread(target=client_thread, args=(conn, addr), daemon=True).start()

if __name__ == "__main__":
    start_server()

