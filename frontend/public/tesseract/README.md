# Offline OCR assets (Tesseract.js)

These files are loaded locally by the PDF reader. No CDN / cloud OCR is used at runtime.

| Path | Purpose |
|------|---------|
| `worker.min.js` | Tesseract web worker |
| `tesseract-core*.wasm(.js)` | WASM OCR engine |
| `lang-data/eng.traineddata` | English (+ IPA-ish Latin) |
| `lang-data/chi_sim.traineddata` | Simplified Chinese |

Language packs are from [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) (speed / size oriented).

If files are missing, restore with:

```bash
mkdir -p public/tesseract/lang-data
cp node_modules/tesseract.js/dist/worker.min.js public/tesseract/
cp node_modules/tesseract.js-core/tesseract-core.wasm.js public/tesseract/
cp node_modules/tesseract.js-core/tesseract-core.wasm public/tesseract/
cp node_modules/tesseract.js-core/tesseract-core-simd.wasm.js public/tesseract/
cp node_modules/tesseract.js-core/tesseract-core-simd.wasm public/tesseract/
curl -L -o public/tesseract/lang-data/eng.traineddata \
  https://cdn.jsdelivr.net/gh/tesseract-ocr/tessdata_fast@main/eng.traineddata
curl -L -o public/tesseract/lang-data/chi_sim.traineddata \
  https://cdn.jsdelivr.net/gh/tesseract-ocr/tessdata_fast@main/chi_sim.traineddata
```
