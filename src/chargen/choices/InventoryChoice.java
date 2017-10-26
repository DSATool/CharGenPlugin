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
package chargen.choices;

import dsatool.resources.ResourceManager;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class InventoryChoice extends Choice {
	private final JSONObject actual;

	public InventoryChoice(final String name, final JSONObject actual) {
		final JSONObject items = ResourceManager.getResource("data/Ausruestung");
		JSONObject item;
		if (items.containsKey(name)) {
			item = items.getObj(name).clone(null);
			item.addAll(actual, true);
		} else {
			item = actual.clone(null);
		}
		if (!item.containsKey("Name")) {
			item.put("Name", name);
		}
		this.actual = item;
	}

	@Override
	public void apply(final JSONObject hero, final boolean alreadyApplied) {
		if (alreadyApplied) return;
		final JSONArray target = hero.getObj("Besitz").getArr("Ausrüstung");
		target.add(actual.clone(target));
	}

	@Override
	public void unapply(final JSONObject hero) {
		final JSONArray target = hero.getObj("Besitz").getArr("Ausrüstung");
		target.remove(actual);
	}
}
