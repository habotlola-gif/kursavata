# Обучение нейро-ротации

1. Включи модуль AimRecorder, подерись своей мышкой, выключи. Файлы: `config/hatek/aim_data/aim_*.csv`.
2. `python train.py aim_model.json aim_*.csv` (нужен только numpy).
3. Положи `aim_model.json` в `config/hatek/` — режим Neural Rotation подхватит его вместо встроенного.
