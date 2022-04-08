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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ChargenUtil {

	public static final Collator comparator = Collator.getInstance(Locale.GERMANY);

	public static void collectLanguages(final JSONObject original, final JSONObject modifications) {
		if (original == null || modifications == null) return;

		for (final String type : new String[] { "Muttersprache", "Zweitsprache", "Lehrsprache" }) {
			if (modifications.containsKey(type)) {
				original.put(type, modifications.getArr(type).clone(original));
			}
		}

		if (modifications.containsKey("Muttersprache:Schrift")) {
			original.put("Muttersprache:Schrift", modifications.getObj("Muttersprache:Schrift").clone(original));
		}
	}

	public static void collectModifications(final JSONObject original, final JSONObject modifications) {
		if (original == null || modifications == null) return;

		final JSONArray choices = original.getArr("Wahl");
		for (final String item : modifications.keySet()) {
			if ("Wahl".equals(item)) {
				final JSONArray newChoices = modifications.getArr(item);
				for (int i = 0; i < newChoices.size(); ++i) {
					final JSONObject newChoice = newChoices.getObj(i);
					boolean found = false;
					if (!newChoice.containsKey("Oder")) {
						for (int j = 0; j < choices.size(); ++j) {
							final JSONObject choice = choices.getObj(j);
							if (choice.containsKey("Wahl") && choice.getArr("Wahl").equals(newChoice.getArr("Wahl"))) {
								if (!choice.containsKey("Punkte") && !newChoice.containsKey("Punkte")) {
									final JSONArray values = choice.getArr("Werte");
									final JSONArray newValues = newChoice.getArr("Werte");
									for (int k = 0; k < newValues.size(); ++k) {
										final int modification = newValues.getInt(k);
										if (values.contains(-modification)) {
											values.remove(-modification);
										} else {
											values.add(modification);
										}
									}
									if (newChoice.containsKey("Ausgewählt")) {
										choice.put("Ausgewählt", newChoice.getArr("Ausgewählt").clone(choice));
									}
									if (newChoice.containsKey("Leittalent")) {
										choice.put("Leittalent", newChoice.getBool("Leittalent"));
									}
									found = true;
									break;
								} else if (choice.containsKey("Punkte") && newChoice.containsKey("Punkte")
										&& choice.getIntOrDefault("Minimum", 0).equals(newChoice.getIntOrDefault("Minimum", 0))
										&& choice.getIntOrDefault("Maximum", 0).equals(newChoice.getIntOrDefault("Maximum", 0))
										&& choice.getBoolOrDefault("Verrechnungspunkte", false)
												.equals(newChoice.getBoolOrDefault("Verrechnungspunkte", false))) {
									choice.put("Punkte", choice.getInt("Punkte") + newChoice.getInt("Punkte"));
									if (newChoice.containsKey("Ausgewählt")) {
										choice.put("Ausgewählt", newChoice.getArr("Ausgewählt").clone(choice));
									}
									if (newChoice.containsKey("Leittalent")) {
										choice.put("Leittalent", newChoice.getBool("Leittalent"));
									}
									found = true;
									break;
								}
							} else if (choice.containsKey("Oder")) {
								final JSONArray alternatives = choice.getArr("Oder");
								for (int k = 0; k < alternatives.size(); ++k) {
									final JSONObject alternativeChoice = choices.getObj(j);
									if (alternativeChoice.getArr("Wahl").equals(newChoice.getArr("Wahl"))) {
										if (!alternativeChoice.containsKey("Punkte") && !newChoice.containsKey("Punkte")) {
											final JSONArray values = alternativeChoice.getArr("Werte");
											final JSONArray newValues = newChoice.getArr("Werte");
											for (int l = 0; l < newValues.size(); ++l) {
												final int modification = newValues.getInt(l);
												if (values.contains(-modification)) {
													values.remove(-modification);
												} else {
													values.add(modification);
												}
											}
											if (newChoice.containsKey("Ausgewählt")) {
												alternativeChoice.put("Ausgewählt", newChoice.getArr("Ausgewählt").clone(alternativeChoice));
											}
											if (newChoice.containsKey("Leittalent")) {
												alternativeChoice.put("Leittalent", newChoice.getBool("Leittalent"));
											}
											found = true;
											break;
										} else if (alternativeChoice.containsKey("Punkte") && newChoice.containsKey("Punkte")
												&& alternativeChoice.getIntOrDefault("Minimum", 0).equals(newChoice.getIntOrDefault("Minimum", 0))
												&& alternativeChoice.getIntOrDefault("Maximum", 0).equals(newChoice.getIntOrDefault("Maximum", 0))
												&& alternativeChoice.getBoolOrDefault("Verrechnungspunkte", false)
														.equals(newChoice.getBoolOrDefault("Verrechnungspunkte", false))) {
											alternativeChoice.put("Punkte", alternativeChoice.getInt("Punkte") + newChoice.getInt("Punkte"));
											if (newChoice.containsKey("Ausgewählt")) {
												alternativeChoice.put("Ausgewählt", newChoice.getArr("Ausgewählt").clone(alternativeChoice));
											}
											if (newChoice.containsKey("Leittalent")) {
												alternativeChoice.put("Leittalent", newChoice.getBool("Leittalent"));
											}
											found = true;
											break;
										}
									}
								}
							}
						}
					}
					if (!found) {
						choices.add(newChoice.clone(choices));
					}
				}
			} else {
				if (modifications.getUnsafe(item) instanceof JSONArray) {
					for (int i = 0; i < modifications.getArr(item).size(); ++i) {
						original.getArr(item).add(modifications.getArr(item).getInt(i));
					}
				} else {
					original.put(item, original.getIntOrDefault(item, 0) + modifications.getInt(item));
				}
			}
		}
		if (choices.size() == 0) {
			original.removeKey("Wahl");
		}

		final List<String> toRemove = new LinkedList<>();
		for (final String item : original.keySet()) {
			if ("Wahl".equals(item)) {
				continue;
			}
			if (modifications.getUnsafe(item) instanceof Long && original.getInt(item) == 0) {
				toRemove.add(item);
			}
		}
		for (final String key : toRemove) {
			original.removeKey(key);
		}

		for (int i = 0; i < choices.size(); ++i) {
			final JSONObject choice = choices.getObj(i);
			if (choice.containsKey("Punkte")) {
				if (choice.getInt("Punkte") == 0) {
					choices.removeAt(i);
					--i;
				}
			} else if (choice.containsKey("Oder")) {} else {
				final JSONArray choiceValues = choice.getArr("Werte");
				boolean allZero = true;
				for (int j = 0; j < choiceValues.size(); ++j) {
					if (choiceValues.getInt(j) != 0) {
						allZero = false;
						break;
					}
				}
				if (allZero && !choice.getBoolOrDefault("Leittalent", false)) {
					choices.removeAt(i);
					--i;
				}
			}
		}
	}

	public static void collectSpellModifications(final JSONObject original, final JSONObject modifications) {
		if (original == null || modifications == null) return;

		final JSONArray choices = original.getArr("Wahl");
		for (final String item : modifications.keySet()) {
			if ("Wahl".equals(item)) {
				final JSONArray newChoices = modifications.getArr(item);
				for (int i = 0; i < newChoices.size(); ++i) {
					final JSONObject newChoice = newChoices.getObj(i);
					boolean found = false;
					if (!newChoice.containsKey("Oder")) {
						for (int j = 0; j < choices.size(); ++j) {
							final JSONObject choice = choices.getObj(j);
							if (choice.getObj("Wahl").equals(newChoice.getObj("Wahl"))) {
								if (!choice.containsKey("Punkte") && !newChoice.containsKey("Punkte")) {
									final JSONArray values = choice.getArr("Werte");
									final JSONArray newValues = newChoice.getArr("Werte");
									for (int k = 0; k < newValues.size(); ++k) {
										final int modification = newValues.getInt(k);
										if (values.contains(-modification)) {
											values.remove(-modification);
										} else {
											values.add(modification);
										}
									}
									if (newChoice.containsKey("Ausgewählt")) {
										choice.put("Ausgewählt", newChoice.getArr("Ausgewählt"));
									}
									found = true;
									break;
								} else if (choice.containsKey("Punkte") && newChoice.containsKey("Punkte")
										&& choice.getIntOrDefault("Minimum", 0).equals(newChoice.getIntOrDefault("Minimum", 0))
										&& choice.getIntOrDefault("Maximum", 0).equals(newChoice.getIntOrDefault("Maximum", 0))
										&& choice.getBoolOrDefault("Verrechnungspunkte", false)
												.equals(newChoice.getBoolOrDefault("Verrechnungspunkte", false))) {
									choice.put("Punkte", choice.getInt("Punkte") + newChoice.getInt("Punkte"));
									if (newChoice.containsKey("Ausgewählt")) {
										choice.put("Ausgewählt", newChoice.getArr("Ausgewählt"));
									}
									found = true;
									break;
								}
							}
						}
					}
					if (!found) {
						choices.add(newChoices.getObj(i).clone(choices));
					}
				}
			} else {
				final JSONObject originalSpell = original.getObj(item);
				final JSONObject modification = modifications.getObj(item);
				for (final String rep : modification.keySet()) {
					if (modification.getUnsafe(rep) instanceof JSONArray) {
						for (int i = 0; i < modification.getArr(rep).size(); ++i) {
							originalSpell.getArr(rep).add(modification.getArr(rep).getInt(i));
						}
					} else {
						originalSpell.put(rep, originalSpell.getIntOrDefault(rep, 0) + modification.getInt(rep));
					}
				}
			}
		}
		if (choices.size() == 0) {
			original.removeKey("Wahl");
		}

		final List<String> toRemove = new LinkedList<>();
		for (final String item : original.keySet()) {
			if ("Wahl".equals(item)) {
				continue;
			}
			final JSONObject originalSpell = original.getObj(item);
			final List<String> repsToRemove = new LinkedList<>();
			for (final String rep : originalSpell.keySet()) {
				if (originalSpell.getUnsafe(rep) instanceof Long && originalSpell.getInt(rep) == 0) {
					repsToRemove.add(rep);
				}
			}
			for (final String rep : repsToRemove) {
				originalSpell.removeKey(rep);
			}
			if (originalSpell.size() == 0) {
				toRemove.add(item);
			}
		}
		for (final String key : toRemove) {
			original.removeKey(key);
		}

		for (int i = 0; i < choices.size(); ++i) {
			final JSONObject choice = choices.getObj(i);
			if (choice.containsKey("Punkte")) {
				if (choice.getInt("Punkte") == 0) {
					choices.removeAt(i);
					--i;
				}
			} else if (choice.containsKey("Oder")) {} else {
				final JSONArray choiceValues = choice.getArr("Werte");
				boolean allZero = true;
				for (int j = 0; j < choiceValues.size(); ++j) {
					if (choiceValues.getInt(j) != 0) {
						allZero = false;
						break;
					}
				}
				if (allZero) {
					choices.removeAt(i);
					--i;
				}
			}
		}
	}

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
