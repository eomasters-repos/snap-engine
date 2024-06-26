package org.esa.stac.reader;

import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.stac.internal.StacItem;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

// Class to convert a STAC Item JSON structure into SNAP metadata structure
public class STACMetadataFactory {


    private JSONObject itemJSON;
    public STACMetadataFactory(StacItem item){
        this.itemJSON = item.getItemJSON();
    }
    public STACMetadataFactory(JSONObject itemJSON){
        this.itemJSON = itemJSON;
    }

    public MetadataElement generate(){
        MetadataElement root = new MetadataElement(AbstractMetadata.ORIGINAL_PRODUCT_METADATA);
        buildMetadata(root, itemJSON);
        return root;
    }

    private void buildMetadata(MetadataElement parentElement, JSONObject json) {
        for (Object keyObject : json.keySet()) {
            String key = (String) keyObject;
            Object value = json.get(key);

            if (value instanceof JSONObject) {
                // Create a MetadataElement for nested objects
                MetadataElement nestedElement = new MetadataElement(key);
                parentElement.addElement(nestedElement);
                buildMetadata(nestedElement, (JSONObject) value);
            } else if (value instanceof JSONArray) {
                // Create a MetadataElement for arrays
                MetadataElement arrayElement = new MetadataElement(key);
                parentElement.addElement(arrayElement);

                JSONArray jsonArray = (JSONArray) value;
                for (int i = 0; i < jsonArray.size(); i++) {
                    Object arrayValue = jsonArray.get(i);
                    if (arrayValue instanceof JSONObject) {
                        // Recursively build MetadataElement for objects within the array
                        MetadataElement nestedArrayElement = new MetadataElement("element");
                        parentElement.addElement(nestedArrayElement);
                        buildMetadata(nestedArrayElement, (JSONObject) arrayValue);
                    } else {
                        // Create MetadataAttribute for non-object values within the array
                        AbstractMetadata.setAttribute(arrayElement, "element", arrayValue.toString());
                    }
                }
            } else {
                // Create MetadataAttribute for non-object values
                AbstractMetadata.setAttribute(parentElement, key, value.toString());
            }
        }
    }
}
