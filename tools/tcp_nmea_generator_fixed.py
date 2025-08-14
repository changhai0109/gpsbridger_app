import math
import time

lat0 = 37.0       # center latitude
lon0 = -122.0     # center longitude
R = 10000         # 10 km radius in meters
v = 2             # 2 m/s speed

circumference = 2 * math.pi * R
T = circumference / v  # seconds to complete circle
omega = 2 * math.pi / T

start_time = time.time()

def generate_fake_gprmc_circle():
    t = time.time() - start_time
    delta_lat = R / 111111
    delta_lon = R / (111111 * math.cos(math.radians(lat0)))

    lat = lat0 + delta_lat * math.sin(omega * t)
    lon = lon0 + delta_lon * math.cos(omega * t)

    # Convert to NMEA format
    lat_d = int(abs(lat))
    lat_m = (abs(lat) - lat_d) * 60
    lat_dir = "N" if lat >= 0 else "S"

    lon_d = int(abs(lon))
    lon_m = (abs(lon) - lon_d) * 60
    lon_dir = "E" if lon >= 0 else "W"

    lat_str = f"{lat_d:02d}{lat_m:07.4f}"
    lon_str = f"{lon_d:03d}{lon_m:07.4f}"

    hhmmss = time.strftime("%H%M%S", time.gmtime())
    date = time.strftime("%d%m%y", time.gmtime())
    speed = f"{v*3.6:.1f}"  # convert m/s to km/h
    track = f"{(math.degrees(math.atan2(omega * delta_lat * math.cos(omega*t),-omega * delta_lon * math.sin(omega*t)))+360)%360:.1f}"

    sentence_body = f"GPRMC,{hhmmss}.00,A,{lat_str},{lat_dir},{lon_str},{lon_dir},{speed},{track},{date},,,A"
    
    # XOR checksum
    cksum = 0
    for c in sentence_body:
        cksum ^= ord(c)
    sentence = f"${sentence_body}*{cksum:02X}"
    return sentence

def start_server(): 
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s: 
        s.bind((HOST, PORT)) 
        s.listen(1) 
        print(f"Fake NMEA server listening on {HOST}:{PORT}") 
        conn, addr = s.accept() 
        with conn: 
            print(f"Client connected: {addr}") 
            while True: 
                try: 
                    nmea = generate_fake_gprmc() 
                    conn.sendall((nmea + "\r\n").encode("ascii"))
                    time.sleep(1) # send 1 sentence per second
                except (BrokenPipeError, ConnectionResetError): 
                    print("Client disconnected") 
                    conn, addr = s.accept() 
                    print(f"New client connected: {addr}") 
if __name__ == "__main__": 
    start_server()
