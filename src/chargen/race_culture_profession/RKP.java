/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package chargen.race_culture_profession;

import java.util.ArrayList;
import java.util.List;

import dsatool.util.Tuple;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class RKP {
	public enum Type {
		Race, Culture, Profession
	}

	final Type type;
	final String name;
	final JSONObject data;
	final RKP parent;
	final List<RKP> variants;

	final BooleanProperty valid = new SimpleBooleanProperty(true);
	final BooleanProperty suggested = new SimpleBooleanProperty(false);

	final int depth;

	public RKP(final Type type, final String name, final JSONObject data, final RKP parent) {
		this.type = type;
		this.name = name;
		this.data = data;
		this.parent = parent;

		if (parent != null) {
			variants = new ArrayList<>(parent.getVariants());
			depth = parent.depth + 1;
		} else {
			variants = new ArrayList<>();
			depth = 1;
		}
		final JSONObject actualVariants = data.getObjOrDefault("Varianten", null);
		if (actualVariants != null) {
			for (final String variantName : actualVariants.keySet()) {
				if (actualVariants.getObj(variantName).getBoolOrDefault("kombinierbar", false)) {
					variants.add(new RKP(type, variantName, actualVariants.getObj(variantName), this));
				}
			}
		}
	}

	public Integer getCost(final Integer defaultValue) {
		return RKPSelectors.getInt(this, "Kosten", defaultValue);
	}

	public Tuple<JSONArray, JSONArray> getSuggestedOrPossible() {
		return switch (type) {
			case Race -> new Tuple<>(data.getArrOrDefault("Übliche Kulturen", null), data.getArrOrDefault("Mögliche Kulturen", null));
			case Culture -> new Tuple<>(null, data.getArrOrDefault("Professionen", null));
			default -> new Tuple<>(null, null);
		};
	}

	public List<RKP> getVariants() {
		return variants;
	}

	@Override
	public String toString() {
		final Integer cost = getCost(null);
		return name + " (" + (cost == null ? "GP nach Variante" : getCost(0) + " GP") + ')';
	}
}
