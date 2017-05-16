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

import dsatool.resources.ResourceManager;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import jsonant.value.JSONObject;

public class SkillSelector extends ProConSkillSelector {

	private final List<GroupSelector> selectors = new ArrayList<>();

	public SkillSelector(JSONObject generationState, IntegerProperty gp, String type) {
		super(generationState, gp, type, null, null);
		final VBox box = new VBox(2);
		box.setMaxWidth(Double.POSITIVE_INFINITY);
		box.setFillWidth(true);
		possiblePane.setContent(box);

		final JSONObject skills = ResourceManager.getResource("data/Sonderfertigkeiten");
		for (final String groupName : skills.keySet()) {
			final GroupSelector selector = new GroupSelector(generationState, type, this, skills.getObj(groupName), 0);
			box.getChildren().add(new TitledPane(groupName, selector.getControl()));
			selectors.add(selector);
		}

		final JSONObject liturgies = ResourceManager.getResource("data/Liturgien");
		final GroupSelector liturgiesSelector = new GroupSelector(generationState, type, this, liturgies, 0);
		box.getChildren().add(new TitledPane("Liturgien", liturgiesSelector.getControl()));
		selectors.add(liturgiesSelector);

		final JSONObject rituals = ResourceManager.getResource("data/Rituale");
		for (final String groupName : rituals.keySet()) {
			final GroupSelector selector = new GroupSelector(generationState, type, this, rituals.getObj(groupName), 0);
			box.getChildren().add(new TitledPane(groupName, selector.getControl()));
			selectors.add(selector);
		}

		if (!"Verbilligte Sonderfertigkeiten".equals(type)) {
			chosenValueColumn.setVisible(false);
			DoubleBinding width = chosenTable.widthProperty().subtract(2);
			width = width.subtract(chosenDescColumn.widthProperty()).subtract(chosenVariantColumn.widthProperty()).subtract(chosenCostColumn.widthProperty());
			chosenNameColumn.prefWidthProperty().bind(width);
		}
	}

	@Override
	protected void activateGroupSelectors(JSONObject hero, JSONObject target) {
		for (final GroupSelector selector : selectors) {
			selector.activate(hero, target);
		}
	}

	@Override
	protected void deactivateGroupSelectors(JSONObject hero, JSONObject target) {
		for (final GroupSelector selector : selectors) {
			selector.deactivate(hero, target);
		}
	}
}