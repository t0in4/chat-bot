# save as merge_model.py
import onnx
from onnx import load_model, save_model

# Load the model with external data
model_path = "model.onnx"
output_path = "model_merged.onnx"

print("Loading model...")
model = load_model(model_path)

print("Saving as single file (embedding data inside)...")
# save_model with convert_attribute_to_tensor=True forces internal storage if possible,
# but primarily we just need to ensure the data path is resolved relative to the file.
# However, the robust way for Java is to inline the data.
save_model(model, output_path, save_as_external_data=False)

print(f"Done! New file: {output_path}")
"""
After running this:
Update your Java code to point to model_merged.onnx.
You can delete model.onnx_data afterwards.
You have to download model.onnx_data size is 2 Gb
"""
