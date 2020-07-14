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
package chargen.pros_cons_skills;

import dsa41basis.hero.ProOrCon;
import dsa41basis.util.HeroUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jsonant.value.JSONObject;

public class ProConOrSkill extends ProOrCon {

	private final boolean fixed;
	private final boolean fixedChoice;
	private final boolean fixedText;

	private final boolean isSkills;
	private final boolean isCheaperSkills;
	private final boolean isInvalid;

	private final BooleanProperty suggested;

	public ProConOrSkill(final String name, final JSONObject hero, final JSONObject proOrCon, final JSONObject actual, final boolean fixed,
			final boolean fixedChoice, final boolean fixedText, final boolean isSkills, final boolean isCheaperSkills, final boolean isInvalid,
			final boolean suggested) {
		super(name, hero, proOrCon, actual);

		if (!fixedChoice && proOrCon.containsKey("Auswahl") && !actual.containsKey("temporary:SetChoice")) {
			actual.put("temporary:SetChoice", true);
		}
		if (!fixedText && proOrCon.containsKey("Freitext") && !actual.containsKey("temporary:SetText")) {
			actual.put("temporary:SetText", true);
		}

		this.fixed = fixed;
		this.fixedChoice = fixedChoice;
		this.fixedText = fixedText;

		this.isSkills = isSkills;
		this.isCheaperSkills = isCheaperSkills;
		this.isInvalid = isInvalid;

		this.suggested = new SimpleBooleanProperty(suggested);

		updateValid();
	}

	@Override
	public ChoiceOrTextEnum firstChoiceOrText() {
		if (fixedChoice) return ChoiceOrTextEnum.NONE;
		final ChoiceOrTextEnum res = super.firstChoiceOrText();
		if (fixedText && res == ChoiceOrTextEnum.TEXT) return ChoiceOrTextEnum.NONE;
		return res;
	}

	@Override
	public int getMaxValue() {
		return Math.max(getValue(), super.getMaxValue());
	}

	@Override
	public int getMinValue() {
		return Math.max(getValue() - getActual().getIntOrDefault("temporary:AdditionalLevels", 0), super.getMinValue());
	}

	public boolean hasFixedChoice() {
		return fixedChoice;
	}

	public boolean hasFixedText() {
		return fixedText;
	}

	public boolean isFixed() {
		return fixed;
	}

	public boolean isSuggested() {
		return suggested.get();
	}

	public boolean isValid() {
		return valid.get();
	}

	@Override
	public ChoiceOrTextEnum secondChoiceOrText() {
		if (fixedText) return ChoiceOrTextEnum.NONE;
		return super.secondChoiceOrText();
	}

	@Override
	public void setDescription(final String description, final boolean applyEffect) {
		if (applyEffect) {
			HeroUtil.unapplyEffect(hero, name.get(), proOrCon, actual);
		}
		if (proOrCon.containsKey("Auswahl") || "Auswahl".equals(proOrCon.getString("Spezialisierung"))) {
			actual.put("Auswahl", description);
		} else if (proOrCon.containsKey("Freitext")) {
			actual.put("Freitext", description);
		}
		if (applyEffect) {
			HeroUtil.applyEffect(hero, name.get(), proOrCon, actual);
		}
		this.description.set(description);
		updateCost(value.get(), actual.getString("Auswahl"), actual.getString("Freitext"));
		actual.notifyListeners(null);
		if (getProOrCon().containsKey("Auswahl")) {
			if (!fixedChoice) {
				getActual().put("temporary:SetChoice", true);
			}
		} else if (!fixedText) {
			getActual().put("temporary:SetText", true);
		}
		updateValid();
	}

	@Override
	public void setNumCheaper(final int numCheaper) {
		actual.put("temporary:AdditionalLevels", actual.getIntOrDefault("temporary:AdditionalLevels", 0) + numCheaper - getNumCheaper());
		if (numCheaper > 1) {
			actual.put("Verbilligungen", numCheaper);
		} else {
			actual.removeKey("Verbilligungen");
		}
		updateCost(value.get(), actual.getString("Auswahl"), actual.getString("Freitext"));
		this.numCheaper.set(numCheaper);
		actual.notifyListeners(null);
	}

	@Override
	public void setValue(final int value) {
		actual.put("temporary:AdditionalLevels", actual.getIntOrDefault("temporary:AdditionalLevels", 0) + value - getValue());
		if (!actual.getBoolOrDefault("temporary:suppressEffects", false)) {
			HeroUtil.unapplyEffect(hero, name.get(), proOrCon, actual);
		}
		actual.put("Stufe", value);
		if (!actual.getBoolOrDefault("temporary:suppressEffects", false)) {
			HeroUtil.applyEffect(hero, name.get(), proOrCon, actual);
		}
		updateCost(value, actual.getString("Auswahl"), actual.getString("Freitext"));
		this.value.set(value);
		actual.notifyListeners(null);
	}

	@Override
	public void setVariant(final String variant, final boolean applyEffect) {
		if (applyEffect) {
			HeroUtil.unapplyEffect(hero, name.get(), proOrCon, actual);
		}
		if (proOrCon.containsKey("Auswahl") && proOrCon.containsKey("Freitext")) {
			actual.put("Freitext", variant);
		}
		if (applyEffect) {
			HeroUtil.applyEffect(hero, name.get(), proOrCon, actual);
		}
		this.variant.set(variant);
		updateCost(value.get(), actual.getString("Auswahl"), actual.getString("Freitext"));
		if (!fixedText) {
			getActual().put("temporary:SetText", true);
		}
		updateValid();
	}

	public ReadOnlyBooleanProperty suggestedProperty() {
		return suggested;
	}

	@Override
	protected void updateValid() {
		super.updateValid();
		valid.set(!isInvalid && (isCheaperSkills || valid.get() && (isSkills ? proOrCon.getIntOrDefault("Verbreitung", 1) > 3 : true)));
	}
}
