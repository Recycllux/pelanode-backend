# Climate grids

Source: organiser hackathon share, `gee_output/precipitation` and `gee_output/soil_moisture`
(Google Earth Engine exports over Bulgaria, one GeoTIFF per month of 2024).

- `precipitation.csv`: 9,520 cells (140 x 68), monthly total in mm.
- `soil_moisture.csv`: 2,414 cells (71 x 34), monthly mean volumetric soil water in m3/m3.

The TIFFs carry no georeference tags. Cell coordinates come from the CSV export of the same grid
(row-major from the north-west corner); the values were checked against the TIFFs.
Rebuild with `python3 scripts/build_climate_pack.py <Share/gee_output> data/climate`.

The grids are 2024 while the Danube gauge pack is 2021, so `/api/v1/danube/point` matches them by
calendar month and says so in its `note` field.
