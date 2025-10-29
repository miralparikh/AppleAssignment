High-Level Overview of ForecastApp
1.User enters a ZIP code in the UI.
2.The app checks if the data for that ZIP is cached (less than 30 min old).
3.If not cached:
* Calls Zippopotam.us API to convert ZIP → latitude & longitude.
* Calls Open-Meteo API to fetch:
      * Current temperature
      * Daily maximum and minimum temperatures

    * Converts Celsius → Fahrenheit
    * Stores the result in cache for 30 minutes.

4.Displays in the UI:
  * Current temperature
  * Today’s high and low
  * Next 3 days’ forecast (high & low)
  * Cache indicator (⚡ from cache / ☁️ live data)