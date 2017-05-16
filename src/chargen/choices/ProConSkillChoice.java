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

import chargen.util.ChargenUtil;
import dsa41basis.util.HeroUtil;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ProConSkillChoice extends Choice {
	private final String name;
	private final JSONObject actual;
	private final JSONObject target;
	private final boolean isLeveled;
	private final JSONObject proConSkill;
	private final boolean hasChoice;
	private final boolean hasFreetext;
	private final boolean cheaperSkills;
	private boolean pooled = false;

	public ProConSkillChoice(String name, JSONObject actual, JSONObject target, boolean cheaperSkills) {
		this.name = name;
		this.actual = actual;
		this.target = target;
		this.cheaperSkills = cheaperSkills;
		proConSkill = HeroUtil.findProConOrSkill(name)._1;
		isLeveled = proConSkill != null && proConSkill.getBoolOrDefault("Abgestuft", false);
		hasChoice = proConSkill != null && proConSkill.containsKey("Auswahl");
		hasFreetext = proConSkill != null && proConSkill.containsKey("Freitext");
	}

	@Override
	public void apply(JSONObject hero, boolean alreadyApplied) {
		if (alreadyApplied) return;
		if (hasChoice || hasFreetext) {
			final JSONArray targetArray = target.getArr(name);
			final JSONObject match = ChargenUtil.match(targetArray, actual, hasChoice, hasFreetext);
			if (match != null) {
				if (isLeveled) {
					match.put("Stufe", match.getIntOrDefault("Stufe", 0) + actual.getIntOrDefault("Stufe", 0));
					HeroUtil.applyEffect(hero, name, proConSkill, actual);
				} else if (cheaperSkills) {
					match.put("Verbilligungen", match.getIntOrDefault("Verbilligungen", 1) + 1);
				} else {
					pooled = true;
					target.put("temporary:Pool", target.getIntOrDefault("temporary:Pool", 0) + proConSkill.getIntOrDefault("Kosten", 0));
				}
			} else {
				targetArray.add(actual.clone(targetArray));
				if (!cheaperSkills) {
					HeroUtil.applyEffect(hero, name, proConSkill, actual);
				}
			}
		} else {
			if (!target.containsKey(name) || cheaperSkills) {
				if (target.containsKey(name)) {
					target.getObj(name).put("Verbilligungen", target.getObj(name).getIntOrDefault("Verbilligungen", 1) + 1);
				} else {
					target.put(name, actual.clone(target));
				}
				if (!cheaperSkills) {
					HeroUtil.applyEffect(hero, name, proConSkill, actual);
				}
			} else if (isLeveled) {
				final JSONObject actualTarget = target.getObj(name);
				actualTarget.put("Stufe", actualTarget.getIntOrDefault("Stufe", 0) + actual.getIntOrDefault("Stufe", 0));
				HeroUtil.applyEffect(hero, name, proConSkill, actual);
			} else {
				pooled = true;
				target.put("temporary:Pool", target.getIntOrDefault("temporary:Pool", 0) + proConSkill.getIntOrDefault("Kosten", 0));
			}
		}
	}

	@Override
	public void unapply(JSONObject hero) {
		if (pooled) {
			pooled = false;
			target.put("temporary:Pool", target.getIntOrDefault("temporary:Pool", 0) - proConSkill.getIntOrDefault("Kosten", 0));
		}
		if (hasChoice || hasFreetext) {
			final JSONArray targetArray = target.getArr(name);
			final JSONObject match = ChargenUtil.match(targetArray, actual, hasChoice, hasFreetext);
			if (isLeveled) {
				match.put("Stufe", match.getIntOrDefault("Stufe", 0) - actual.getIntOrDefault("Stufe", 0));
			}
			if (!isLeveled || match.getInt("Stufe") == 0) {
				if (cheaperSkills && match.getIntOrDefault("Verbilligungen", 1) != 1) {
					match.put("Verbilligungen", match.getInt("Verbilligungen") - 1);
				} else {
					targetArray.remove(match);
				}
			}
			if (!cheaperSkills) {
				HeroUtil.unapplyEffect(hero, name, proConSkill, actual);
			}
			if (targetArray.size() == 0) {
				target.removeKey(name);
			}
		} else {
			if (isLeveled) {
				final JSONObject actualTarget = target.getObj(name);
				actualTarget.put("Stufe", actualTarget.getIntOrDefault("Stufe", 0) - actual.getIntOrDefault("Stufe", 0));
				if (actualTarget.getInt("Stufe") == 0) {
					target.removeKey(name);
				}
			} else {
				if (cheaperSkills && target.getObj(name).getIntOrDefault("Verbilligungen", 1) != 1) {
					target.getObj(name).put("Verbilligungen", target.getObj(name).getInt("Verbilligungen") - 1);
				} else {
					target.removeKey(name);
				}
			}
			if (!cheaperSkills) {
				HeroUtil.unapplyEffect(hero, name, proConSkill, actual);
			}
		}
	}
}
