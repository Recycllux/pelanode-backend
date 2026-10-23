#!/usr/bin/env python3
"""Build the Danube level pack from the two organiser CSVs (EIT Water Hackathon, Share/Danube).

Inputs (Bulgarian headers, UTF-8):
  - БюлетинЗаХидрологичнатаОбстановкаПоРекаДунав.csv  daily level, whole Danube + Tisza, Sava, Velika Morava
  - АвтоматичниXидрометричниСтанции.csv              Bulgarian bank automatic gauges, level + water temperature

Cleaning: separator rows (empty station), the "притоци" header rows, duplicate (date, station) rows,
the -987 sentinel and "nil" are dropped. Values are kept as published (cm above gauge zero, °C).
Station coordinates are not in the source data: the table below holds approximate town or bridge
positions, flagged coords_approx=true.

Usage: python3 scripts/build_danube_pack.py <Share/Danube dir> [output_dir]   (default data/danube)
"""
import csv
import sys
from pathlib import Path

SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("Share/Danube")
OUT = Path(sys.argv[2] if len(sys.argv) > 2 else "data/danube")
BULLETIN = "БюлетинЗаХидрологичнатаОбстановкаПоРекаДунав.csv"
AUTOMATIC = "АвтоматичниXидрометричниСтанции.csv"
MISSING = {"", "nil", "-987"}

# source name -> (station_id, English name, country ISO, river, lat, lon)
STATIONS = {
    "Швабелвайс": ("SCHWABELWEIS", "Schwabelweis", "DE", "Danube", 49.03, 12.14),
    "Пфелинг": ("PFELLING", "Pfelling", "DE", "Danube", 48.88, 12.75),
    "Хофкирхен": ("HOFKIRCHEN", "Hofkirchen", "DE", "Danube", 48.68, 13.12),
    "Енгелхартсцел": ("ENGELHARTSZELL", "Engelhartszell", "AT", "Danube", 48.51, 13.73),
    "Линц": ("LINZ", "Linz", "AT", "Danube", 48.31, 14.29),
    "Мелк": ("MELK", "Melk", "AT", "Danube", 48.23, 15.33),
    "Кинщок": ("KIENSTOCK", "Kienstock", "AT", "Danube", 48.38, 15.46),
    "Корнойбург": ("KORNEUBURG", "Korneuburg", "AT", "Danube", 48.35, 16.33),
    "Вилдунгсмауер": ("WILDUNGSMAUER", "Wildungsmauer", "AT", "Danube", 48.12, 16.80),
    "Девин": ("DEVIN", "Devin", "SK", "Danube", 48.17, 16.98),
    "Медведьов": ("MEDVEDOV", "Medvedov", "SK", "Danube", 47.79, 17.65),
    "Гьоню": ("GONYU", "Gonyu", "HU", "Danube", 47.74, 17.83),
    "Комаром": ("KOMAROM", "Komarom", "HU", "Danube", 47.75, 18.12),
    "Комарно": ("KOMARNO", "Komarno", "SK", "Danube", 47.76, 18.13),
    "Надмарош": ("NAGYMAROS", "Nagymaros", "HU", "Danube", 47.79, 18.96),
    "Будапеща": ("BUDAPEST", "Budapest", "HU", "Danube", 47.49, 19.05),
    "Дунауйварош": ("DUNAUJVAROS", "Dunaujvaros", "HU", "Danube", 46.96, 18.94),
    "Дунафелдвар": ("DUNAFOLDVAR", "Dunafoldvar", "HU", "Danube", 46.81, 18.93),
    "Пакш": ("PAKS", "Paks", "HU", "Danube", 46.62, 18.86),
    "Байя": ("BAJA", "Baja", "HU", "Danube", 46.18, 18.94),
    "Мохач": ("MOHACS", "Mohacs", "HU", "Danube", 45.99, 18.69),
    "Бездан": ("BEZDAN", "Bezdan", "RS", "Danube", 45.85, 18.86),
    "Апатин": ("APATIN", "Apatin", "RS", "Danube", 45.67, 18.98),
    "Богоево": ("BOGOJEVO", "Bogojevo", "RS", "Danube", 45.53, 19.13),
    "Вуковар": ("VUKOVAR", "Vukovar", "HR", "Danube", 45.35, 19.00),
    "Нови сад": ("NOVI_SAD", "Novi Sad", "RS", "Danube", 45.25, 19.85),
    "Земун": ("ZEMUN", "Zemun", "RS", "Danube", 44.85, 20.41),
    "Панчево": ("PANCEVO", "Pancevo", "RS", "Danube", 44.86, 20.64),
    "Смедерево": ("SMEDEREVO", "Smederevo", "RS", "Danube", 44.67, 20.93),
    "Базиаш": ("BAZIAS", "Bazias", "RO", "Danube", 44.81, 21.38),
    "Велико градище": ("VELIKO_GRADISTE", "Veliko Gradiste", "RS", "Danube", 44.76, 21.52),
    "Молдова Веке": ("MOLDOVA_VECHE", "Moldova Veche", "RO", "Danube", 44.72, 21.63),
    "Дренкова": ("DRENCOVA", "Drencova", "RO", "Danube", 44.66, 21.87),
    "Оршова": ("ORSOVA", "Orsova", "RO", "Danube", 44.72, 22.40),
    "Турну Северин": ("TURNU_SEVERIN", "Drobeta-Turnu Severin", "RO", "Danube", 44.63, 22.66),
    "Прахово": ("PRAHOVO", "Prahovo", "RS", "Danube", 44.29, 22.60),
    "Груя": ("GRUIA", "Gruia", "RO", "Danube", 44.27, 22.70),
    "Калафат": ("CALAFAT", "Calafat", "RO", "Danube", 43.99, 22.94),
    "Бекет": ("BECHET", "Bechet", "RO", "Danube", 43.77, 23.96),
    "Корабия": ("CORABIA", "Corabia", "RO", "Danube", 43.77, 24.50),
    "Турну Мъгуреле": ("TURNU_MAGURELE", "Turnu Magurele", "RO", "Danube", 43.75, 24.87),
    "Зимнич": ("ZIMNICEA", "Zimnicea", "RO", "Danube", 43.65, 25.37),
    "Гюргево": ("GIURGIU", "Giurgiu", "RO", "Danube", 43.88, 25.97),
    "Олтеница": ("OLTENITA", "Oltenita", "RO", "Danube", 44.08, 26.64),
    "Кълараш": ("CALARASI", "Calarasi", "RO", "Danube", 44.20, 27.33),
    "Чернавода": ("CERNAVODA", "Cernavoda", "RO", "Danube", 44.34, 28.03),
    "Хършова": ("HARSOVA", "Harsova", "RO", "Danube", 44.69, 27.95),
    "Браила": ("BRAILA", "Braila", "RO", "Danube", 45.27, 27.97),
    "Галац": ("GALATI", "Galati", "RO", "Danube", 45.43, 28.05),
    "Исакча": ("ISACCEA", "Isaccea", "RO", "Danube", 45.27, 28.46),
    "Тулча": ("TULCEA", "Tulcea", "RO", "Danube", 45.18, 28.80),
    "Сента": ("SENTA", "Senta", "RS", "Tisza", 45.93, 20.09),
    "Нови Бечей": ("NOVI_BECEJ", "Novi Becej", "RS", "Tisza", 45.60, 20.13),
    "Тител": ("TITEL", "Titel", "RS", "Tisza", 45.21, 20.29),
    "С. Митровица": ("SREMSKA_MITROVICA", "Sremska Mitrovica", "RS", "Sava", 44.97, 19.61),
    "Шабац": ("SABAC", "Sabac", "RS", "Sava", 44.76, 19.70),
    "Белград": ("BEOGRAD_SAVA", "Belgrade (Sava)", "RS", "Sava", 44.82, 20.45),
    "Варварин": ("VARVARIN", "Varvarin", "RS", "Velika Morava", 43.72, 21.37),
    "Багрдан": ("BAGRDAN", "Bagrdan", "RS", "Velika Morava", 44.08, 21.18),
    "Любич. мост": ("LJUBICEVSKI_MOST", "Ljubicevski most", "RS", "Velika Morava", 44.59, 21.13),
    "Ново село": ("NOVO_SELO", "Novo Selo", "BG", "Danube", 44.16, 22.78),
    "Гомотарци": ("GOMOTARTSI", "Gomotartsi", "BG", "Danube", 44.10, 22.95),
    "мост Видин": ("VIDIN_BRIDGE", "Vidin bridge", "BG", "Danube", 43.97, 22.92),
    "Лом": ("LOM", "Lom", "BG", "Danube", 43.83, 23.24),
    "Козлодуй": ("KOZLODUY", "Kozloduy", "BG", "Danube", 43.79, 23.73),
    "Горни Вадин": ("GORNI_VADIN", "Gorni Vadin", "BG", "Danube", 43.71, 24.07),
    "Байкал": ("BAYKAL", "Baykal", "BG", "Danube", 43.71, 24.42),
    "Загражден": ("ZAGRAZHDEN", "Zagrazhden", "BG", "Danube", 43.73, 24.52),
    "Никопол": ("NIKOPOL", "Nikopol", "BG", "Danube", 43.70, 24.89),
    "Свищов": ("SVISHTOV", "Svishtov", "BG", "Danube", 43.62, 25.35),
    "мост Русе": ("RUSE_BRIDGE", "Ruse bridge", "BG", "Danube", 43.89, 25.99),
    "Ряхово": ("RYAHOVO", "Ryahovo", "BG", "Danube", 43.98, 26.28),
    "Малък Преславец": ("MALAK_PRESLAVETS", "Malak Preslavets", "BG", "Danube", 44.10, 26.84),
}


def number(text):
    text = (text or "").strip().replace(",", ".")
    if text in MISSING:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def read(name):
    with open(SRC / name, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.reader(f))
    return rows[1:]


def main():
    stations = {}
    levels = {}

    def add(date, name, km, level, temp, source):
        if name not in STATIONS:
            return
        sid = STATIONS[name][0]
        stations.setdefault(name, (km, source))
        key = (date, sid)
        if key in levels or (level is None and temp is None):
            return
        levels[key] = (level, temp, source)

    for date, country, name, km, level, _ in read(BULLETIN):
        name = name.strip()
        if name and country != "притоци":
            add(date, name, number(km), number(level), None, "bulletin")
    for date, name, km, level, _, temp in read(AUTOMATIC):
        name = name.strip()
        if name:
            add(date, name, number(km), number(level), number(temp), "automatic")

    OUT.mkdir(parents=True, exist_ok=True)
    with open(OUT / "stations.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["station_id", "name", "name_bg", "country", "river", "river_km", "lat", "lon",
                    "coords_approx", "source"])
        for name, (km, source) in sorted(stations.items(), key=lambda s: (STATIONS[s[0]][3] != "Danube",
                                                                         STATIONS[s[0]][3], -(s[1][0] or 0))):
            sid, en, country, river, lat, lon = STATIONS[name]
            w.writerow([sid, en, name, country, river, km, lat, lon, "true", source])
    with open(OUT / "levels.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["date", "station_id", "level_cm", "water_temp_c", "source"])
        for (date, sid), (level, temp, source) in sorted(levels.items()):
            w.writerow([date, sid, "" if level is None else f"{level:g}", "" if temp is None else f"{temp:g}", source])
    print(f"stations={len(stations)} level_rows={len(levels)} -> {OUT}")


if __name__ == "__main__":
    main()
