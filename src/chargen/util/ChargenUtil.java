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
package chargen.util;

import java.text.Collator;
import java.util.Locale;

import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ChargenUtil {

	public static final Collator comparator = Collator.getInstance(Locale.GERMANY);

	public static JSONObject match(final JSONArray target, final JSONObject current, final boolean hasChoice, final boolean hasFreetext) {
		if (target == null) return null;
		for (int k = 0; k < target.size(); ++k) {
			boolean matches = true;
			final JSONObject possibleMatch = target.getObj(k);
			if (hasChoice && !possibleMatch.getStringOrDefault("Auswahl", "").equals(current.getStringOrDefault("Auswahl", ""))) {
				matches = false;
			}
			if (hasFreetext && !possibleMatch.getStringOrDefault("Freitext", "").equals(current.getString("Freitext"))) {
				matches = false;
			}
			if (matches) return possibleMatch;
		}
		return null;
	}

	private ChargenUtil() {}
}
