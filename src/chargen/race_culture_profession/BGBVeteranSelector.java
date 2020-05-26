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

import java.util.List;
import java.util.function.Function;

import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class BGBVeteranSelector {
	public enum BGBVeteran {
		NONE, BGB, VETERAN
	}

	@FXML
	private VBox root;
	@FXML
	private RadioButton noneChoice;
	@FXML
	private RadioButton bgbChoice;
	@FXML
	private RadioButton veteranChoice;

	private final Runnable updateValue;
	private final JSONObject generationState;

	private final RKPSelector selector;

	private String professionName;
	private boolean update = true;
	private Function<RKP, Boolean> suggested;
	private Function<RKP, Boolean> possible;

	public BGBVeteranSelector(final Runnable updateValue, final JSONObject generationState) {
		this.updateValue = updateValue;
		this.generationState = generationState;

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("BGBVeteranSelector.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		selector = new RKPSelector(() -> {
			if (update) {
				updateValue.run();
			}
		});
		root.getChildren().add(selector.getControl());
		VBox.setVgrow(selector.getControl(), Priority.ALWAYS);
	}

	public Node getControl() {
		return root;
	}

	public RKP getCurrentChoice() {
		return selector.getCurrentChoice();
	}

	public List<RKP> getCurrentVariants() {
		return selector.getCurrentVariants();
	}

	public BGBVeteran getType() {
		if (bgbChoice.isSelected()) return BGBVeteran.BGB;
		if (veteranChoice.isSelected()) return BGBVeteran.VETERAN;
		return BGBVeteran.NONE;
	}

	public void select(String name, JSONArray modifications) {
		update = false;
		if (veteranChoice.isSelected() && modifications != null && modifications.size() > 0) {
			name = modifications.getString(0);
			modifications = modifications.clone(null);
			modifications.removeAt(0);
		}
		selector.select(name, modifications);
		update = true;
	}

	@FXML
	public void setBGB() {
		final JSONObject professions = ResourceManager.getResource("data/Professionen").clone(null);
		professions.removeKey(professionName);
		selector.setData(professions, t -> new RKP(RKP.Type.Profession, t._1, t._2, t._3));
		updateSuggestedPossible();
		if (update) {
			updateValue.run();
		}
	}

	@FXML
	public void setNone() {
		selector.setData(null, null);
		if (update) {
			updateValue.run();
		}
	}

	public void setProfession(final RKP profession) {
		updateValid();
		if (profession == null) {
			noneChoice.setSelected(true);
			bgbChoice.setDisable(true);
			veteranChoice.setDisable(true);
			setNone();
		} else {
			RKP root = profession;
			while (root.parent != null) {
				root = root.parent;
			}
			professionName = root.name;
			bgbChoice.setDisable(false);
			veteranChoice.setDisable(false);
			if (bgbChoice.isSelected()) {
				setBGB();
			}
			if (veteranChoice.isSelected()) {
				setVeteran();
			}
		}
	}

	public void setSuggestedPossible(final Function<RKP, Boolean> suggested, final Function<RKP, Boolean> possible) {
		this.suggested = suggested;
		this.possible = possible;
		updateSuggestedPossible();
	}

	public void setType(final BGBVeteran type) {
		update = false;
		switch (type) {
			case VETERAN:
				veteranChoice.setSelected(true);
				setVeteran();
				break;
			case BGB:
				bgbChoice.setSelected(true);
				setBGB();
				break;
			default:
				noneChoice.setSelected(true);
				setNone();
				break;
		}
		update = true;
	}

	@FXML
	public void setVeteran() {
		final JSONObject professions = ResourceManager.getResource("data/Professionen");
		selector.setRoot(professionName, professions.getObj(professionName), t -> new RKP(RKP.Type.Profession, t._1, t._2, t._3));
		updateSuggestedPossible();
		if (update) {
			updateValue.run();
		}
	}

	private void updateSuggestedPossible() {
		if (!noneChoice.isSelected() && suggested != null && possible != null) {
			selector.updateSuggestedPossible(suggested, possible);
		} else {
			selector.updateSuggestedPossible(profession -> false, profession -> true);
		}
	}

	public void updateValid() {
		bgbChoice.getStyleClass().remove("invalid");
		veteranChoice.getStyleClass().remove("invalid");
		for (final String type : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject current = generationState.getObjOrDefault(type, null);
			if (current == null) {
				continue;
			}
			final JSONObject invalidPros = current.getObj("Ungeeignete Vorteile");
			if (invalidPros.containsKey("Breitgefächerte Bildung")) {
				bgbChoice.getStyleClass().add("invalid");
			}
			if (invalidPros.containsKey("Veteran")) {
				veteranChoice.getStyleClass().add("invalid");
			}
		}
	}
}
