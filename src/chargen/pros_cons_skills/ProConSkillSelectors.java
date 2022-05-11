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

import java.util.ArrayList;
import java.util.List;

import chargen.ui.TabController;
import chargen.util.ChargenUtil;
import dsa41basis.util.HeroUtil;
import dsatool.resources.Settings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ProConSkillSelectors extends TabController {

	private final ProConSkillSelector proSelector;
	private final ProConSkillSelector conSelector;
	private final SkillSelector skillSelector;

	private final Tab proTab;
	private final Tab conTab;
	private final Tab skillTab;

	private final VBox leftBox;

	private final IntegerProperty conGP = new SimpleIntegerProperty();
	private final IntegerProperty seGP = new SimpleIntegerProperty();

	private final CheckBox showAll = new CheckBox("Alle anzeigen");

	public ProConSkillSelectors(final JSONObject generationState, final TabPane tabPane, final VBox leftBox, final IntegerProperty gp) {
		super(generationState, gp);
		this.leftBox = leftBox;

		proSelector = new ProConSelector(generationState, gp, "Vorteile", null, null, showAll.selectedProperty());
		proTab = addTab(tabPane, "Vorteile", proSelector.getControl());
		conSelector = new ProConSelector(generationState, gp, "Nachteile", conGP, seGP, showAll.selectedProperty());
		conTab = addTab(tabPane, "Nachteile", conSelector.getControl());
		skillSelector = new SkillSelector(generationState, gp, "Sonderfertigkeiten", showAll.selectedProperty());
		skillTab = addTab(tabPane, "SFs", skillSelector.getControl());
	}

	@Override
	public void activate(final boolean forward) {
		proTab.setDisable(false);
		conTab.setDisable(false);
		skillTab.setDisable(false);

		final ObservableList<Node> items = leftBox.getChildren();

		items.add(0, showAll);

		final JSONObject hero = generationState.getObj("Held");

		cleanupCheaperSkills(hero);

		int pos = 2;
		final JSONObject pros = hero.getObj("Vorteile");
		final int proGPPool = pros.getIntOrDefault("temporary:Pool", 0);
		if (proGPPool > 0) {
			items.add(pos, new Label("GP aus doppelten Vorteilen: "));
			final Label proGPLabel = new Label();
			proGPLabel.textProperty().bind(proSelector.getPool().asString().concat("/" + proGPPool));
			items.add(pos + 1, proGPLabel);
			VBox.setMargin(proGPLabel, new Insets(0, 0, 0, 10));
			pos += 2;
		}

		final JSONObject cons = hero.getObj("Nachteile");
		final int conGPPool = cons.getIntOrDefault("temporary:Pool", 0);
		if (conGPPool > 0) {
			items.add(pos, new Label("GP aus doppelten Nachteilen: "));
			final Label conGPLabel = new Label();
			conGPLabel.textProperty().bind(conSelector.getPool().asString().concat("/" + conGPPool));
			items.add(pos + 1, conGPLabel);
			VBox.setMargin(conGPLabel, new Insets(0, 0, 0, 10));
			pos += 2;
		}

		final JSONObject skills = hero.getObj("Sonderfertigkeiten");
		final int skillAPPool = skills.getIntOrDefault("temporary:Pool", 0);
		if (skillAPPool > 0) {
			items.add(pos, new Label("AP aus doppelten Sonderfertigkeiten: "));
			final Label skillAPLabel = new Label();
			skillAPLabel.textProperty().bind(skillSelector.getPool().asString().concat("/" + skillAPPool));
			items.add(pos + 1, skillAPLabel);
			VBox.setMargin(skillAPLabel, new Insets(0, 0, 0, 10));
			pos += 2;
		}

		final JSONObject cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
		final int cheaperSkillAPPool = cheaperSkills.getIntOrDefault("temporary:Pool", 0);
		items.add(pos, new Label("AP aus doppelten verb. Sonderfertigkeiten: "));
		final Label cheaperSkillAPLabel = new Label();
		cheaperSkillAPLabel.textProperty().bind(skillSelector.getCheaperPool().negate().add(cheaperSkillAPPool).asString().concat("/" + cheaperSkillAPPool));
		items.add(pos + 1, cheaperSkillAPLabel);
		VBox.setMargin(cheaperSkillAPLabel, new Insets(0, 0, 0, 10));
		pos += 2;

		final int maxConGP = Settings.getSettingIntOrDefault(50, "Heldenerschaffung", "GP aus Nachteilen");
		conGP.set(0);

		items.add(pos, new Label("GP aus Nachteilen: "));
		final Label conGPLabel = new Label();
		conGPLabel.textProperty().bind(conGP.asString().concat("/" + maxConGP));
		VBox.setMargin(conGPLabel, new Insets(0, 0, 0, 10));
		items.add(pos + 1, conGPLabel);

		final int maxSEGP = Settings.getSettingIntOrDefault(30, "Heldenerschaffung", "GP aus Schlechten Eigenschaften");
		seGP.set(0);

		items.add(pos, new Label("GP aus Schlechten Eigenschaften: "));
		final Label seGPLabel = new Label();
		seGPLabel.textProperty().bind(seGP.asString().concat("/" + maxSEGP));
		VBox.setMargin(seGPLabel, new Insets(0, 0, 0, 10));
		items.add(pos + 1, seGPLabel);

		proSelector.activate(forward);
		conSelector.activate(forward);
		skillSelector.activate(forward);

		proTab.getTabPane().getSelectionModel().select(proTab);

		canContinue.bind(gp.isEqualTo(0).and(conGP.lessThanOrEqualTo(maxConGP)).and(seGP.lessThanOrEqualTo(maxSEGP)).and(proSelector.getPool().isEqualTo(0))
				.and(conSelector.getPool().isEqualTo(0)).and(skillSelector.getPool().isEqualTo(0)).and(skillSelector.getCheaperPool().greaterThanOrEqualTo(0)));
	}

	private void cleanupCheaperSkills(final JSONObject hero) {
		final JSONObject actualSkills = hero.getObj("Sonderfertigkeiten");
		final JSONObject cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");

		final List<String> toRemove = new ArrayList<>();
		int temporary = cheaperSkills.getIntOrDefault("temporary:Pool", 0);
		for (final String name : cheaperSkills.keySet()) {
			if (!actualSkills.containsKey(name) || name.startsWith("temporary")) {
				continue;
			}
			final JSONObject skill = HeroUtil.findSkill(name);
			if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
				final JSONArray cheaperSkillsArray = cheaperSkills.getArr(name);
				for (int i = 0; i < cheaperSkillsArray.size(); ++i) {
					final JSONObject current = cheaperSkillsArray.getObj(i);
					final JSONObject match = ChargenUtil.match(actualSkills.getArr(name), current, skill.containsKey("Auswahl"),
							skill.containsKey("Freitext"));
					if (match != null) {
						temporary += skill.getIntOrDefault("Kosten", 0) * current.getIntOrDefault("Verbilligungen", 1);
						cheaperSkillsArray.removeAt(i);
						--i;
					}
				}
			} else {
				temporary += skill.getIntOrDefault("Kosten", 0) * cheaperSkills.getObj(name).getIntOrDefault("Verbilligungen", 1);
				toRemove.add(name);
			}
		}
		for (final String name : toRemove) {
			cheaperSkills.removeKey(name);
		}
		if (temporary > 0) {
			cheaperSkills.put("temporary:Pool", temporary);
		}
	}

	@Override
	public void deactivate(final boolean forward) {
		proTab.setDisable(true);
		conTab.setDisable(true);
		skillTab.setDisable(true);

		proSelector.deactivate(forward);
		conSelector.deactivate(forward);
		skillSelector.deactivate(forward);

		final ObservableList<Node> items = leftBox.getChildren();
		items.remove(showAll);
		items.remove(1, items.size() - 2);
	}
}
