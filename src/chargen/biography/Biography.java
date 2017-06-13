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
package chargen.biography;

import java.util.ArrayList;
import java.util.List;

import chargen.ui.TabController;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.resources.Settings;
import dsatool.util.ErrorLogger;
import dsatool.util.ReactiveSpinner;
import javafx.beans.property.IntegerProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class Biography extends TabController {

	@FXML
	private Node pane;
	@FXML
	private TextField name;
	@FXML
	private ReactiveSpinner<Integer> size;
	@FXML
	private ReactiveSpinner<Integer> weight;
	@FXML
	private ComboBox<String> eyecolor;
	@FXML
	private ComboBox<String> haircolor;
	@FXML
	private ComboBox<String> skincolor;
	@FXML
	private Label haircolorLabel;
	@FXML
	private Label skincolorLabel;
	@FXML
	private RadioButton male;
	@FXML
	private RadioButton female;
	@FXML
	private Button weightTracksSizeButton;
	@FXML
	private SplitMenuButton namesButton;

	private boolean weightTracksSize = true;
	private boolean scalecolor = false;

	private final JSONObject names;

	private final Tab tab;

	public Biography(final JSONObject generationState, final TabPane tabPane, final VBox leftBox, final IntegerProperty gp) {
		super(generationState, gp);

		final FXMLLoader fxmlLoader = new FXMLLoader();
		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("Biography.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		names = ResourceManager.getResource("data/Namen");

		for (final String name : names.keySet()) {
			final CheckMenuItem item = new CheckMenuItem(name);
			namesButton.getItems().add(item);
		}

		name.textProperty().addListener((o, oldV, newV) -> {
			final JSONObject bio = generationState.getObj("Held").getObj("Biografie");
			bio.put("temporary:customName", true);
			final String fullName = newV.trim();
			final int split = fullName.indexOf(' ');
			if (split < 0) {
				bio.put("Vorname", fullName);
				bio.put("Nachname", "");
			} else {
				bio.put("Vorname", fullName.substring(0, split));
				bio.put("Nachname", fullName.substring(split + 1));
			}
		});

		size.valueProperty().addListener((o, oldV, newV) -> {
			if (weightTracksSize) {
				randomWeight();
			}
			generationState.getObj("Held").getObj("Biografie").put("Größe", newV);
		});

		weight.valueProperty().addListener((o, oldV, newV) -> generationState.getObj("Held").getObj("Biografie").put("Gewicht", newV));

		eyecolor.setItems(FXCollections.observableArrayList(HeroUtil.eyeColors));
		eyecolor.valueProperty().addListener((o, oldV, newV) -> generationState.getObj("Held").getObj("Biografie").put("Augenfarbe", newV.trim()));

		haircolor.valueProperty().addListener((o, oldV, newV) -> {
			generationState.getObj("Held").getObj("Biografie").put(scalecolor ? "Schuppenfarbe 1" : "Haarfarbe", newV.trim());
		});

		skincolor.valueProperty().addListener((o, oldV, newV) -> {
			generationState.getObj("Held").getObj("Biografie").put(scalecolor ? "Schuppenfarbe 2" : "Hautfarbe", skincolor.getValue().trim());
		});

		male.selectedProperty().addListener((o, oldV, newV) -> {
			if (!generationState.getObj("Held").getObj("Biografie").getBoolOrDefault("temporary:customName", false)) {
				randomName();
			}
			generationState.getObj("Held").getObj("Biografie").put("Geschlecht", newV ? "männlich" : "weiblich");
		});

		tab = addTab(tabPane, "Biografie", pane);
	}

	@Override
	public void activate(final boolean forward) {
		if (generationState.getObj("Rasse").containsKey("Schuppenfarbe")) {
			haircolorLabel.setText("Schuppenfarbe 1:");
			skincolorLabel.setText("Schuppenfarbe 2:");
			scalecolor = true;
		} else {
			scalecolor = false;
		}

		final JSONObject bio = generationState.getObj("Held").getObj("Biografie");

		final JSONArray actualNames = generationState.getObj("Kultur").getArr("Namen");
		for (final MenuItem item : namesButton.getItems()) {
			((CheckMenuItem) item).setSelected(actualNames.contains(item.getText()));
		}

		if (bio.containsKey("Vorname")) {
			if (bio.containsKey("Nachname")) {
				name.setText(bio.getString("Vorname") + " " + bio.getString("Nachname"));
			} else {
				name.setText(bio.getString("Vorname"));
			}
		} else {
			randomName();
		}

		if (bio.containsKey("Geschlecht")) {
			final boolean isFemale = bio.getStringOrDefault("Geschlecht", "männlich").equals("weiblich");
			male.setSelected(!isFemale);
			female.setSelected(isFemale);
		} else {
			male.setSelected(true);
			female.setSelected(false);
		}

		if (weightTracksSize != bio.getBoolOrDefault("temporary:weightTracksSize", true)) {
			toggleWeightTracksSize();
		}

		if (bio.containsKey("Gewicht")) {
			weight.getValueFactory().setValue(bio.getInt("Gewicht"));
		}

		if (bio.containsKey("Größe")) {
			size.getValueFactory().setValue(bio.getInt("Größe"));
		} else {
			randomSize();
		}

		if (bio.containsKey("Augenfarbe")) {
			eyecolor.setValue(bio.getString("Augenfarbe"));
		} else {
			randomEyecolor();
		}

		if (scalecolor) {
			haircolor.setItems(FXCollections.observableArrayList(HeroUtil.scaleColors));
			skincolor.setItems(FXCollections.observableArrayList(HeroUtil.scaleColors));
		} else {
			haircolor.setItems(FXCollections.observableArrayList(HeroUtil.hairColors));
			skincolor.setItems(FXCollections.observableArrayList(HeroUtil.skinColors));
		}

		if (bio.containsKey("Haarfarbe")) {
			haircolor.setValue(bio.getString("Haarfarbe"));
		} else if (bio.containsKey("Schuppenfarbe 1")) {
			haircolor.setValue(bio.getString("Schuppenfarbe 1"));
		} else {
			randomHaircolor();
		}

		if (bio.containsKey("Hautfarbe")) {
			skincolor.setValue(bio.getString("Hautfarbe"));
		} else if (bio.containsKey("Schuppenfarbe 2")) {
			skincolor.setValue(bio.getString("Schuppenfarbe 2"));
		} else {
			randomSkincolor();
		}

		tab.setDisable(false);
		tab.getTabPane().getSelectionModel().select(tab);

		canContinue.set(true);
	}

	@Override
	public void deactivate(final boolean forward) {
		tab.setDisable(true);
	}

	@FXML
	private void randomEyecolor() {
		eyecolor.setValue(randomFrom(generationState.getObj("Rasse").getObj("Augenfarbe")));
	}

	private String randomFrom(final JSONObject choices) {
		int roll = DSAUtil.diceRoll(20);
		String choice = "";
		for (final String current : choices.keySet()) {
			roll -= choices.getInt(current);
			if (roll <= 0) {
				choice = current;
				break;
			}
		}
		return choice;
	}

	@FXML
	private void randomHaircolor() {
		haircolor.setValue(randomFrom(generationState.getObj("Rasse").getObj(scalecolor ? "Schuppenfarbe" : "Haarfarbe")));
	}

	@FXML
	private void randomName() {
		final List<String> actualNames = new ArrayList<>();
		for (final MenuItem item : namesButton.getItems()) {
			if (((CheckMenuItem) item).isSelected()) {
				actualNames.add(item.getText());
			}
		}

		final JSONArray generator = names.getArr(actualNames.get(DSAUtil.random.nextInt(actualNames.size())));
		final JSONObject hero = generationState.getObj("Held");
		final boolean isMiddleClass = hero.getObj("Basiswerte").getObj("Sozialstatus").getIntOrDefault("Wert", 1) > 6;
		final boolean isNoble = HeroUtil.isNoble(hero);
		name.setText(DSAUtil.getRandomName(generator, male.isSelected(), isMiddleClass, isNoble));
		hero.getObj("Biografie").removeKey("temporary:customName");
	}

	@FXML
	private void randomSize() {
		final boolean small = generationState.getObj("Held").getObj("Nachteile").containsKey("Kleinwüchsig");
		size.getValueFactory().setValue(HeroUtil.randomSize(generationState.getObj("Rasse").getObj("Größe"), small));
	}

	@FXML
	private void randomSkincolor() {
		String newColor;
		if (scalecolor) {
			newColor = randomFrom(generationState.getObj("Rasse").getObj("Schuppenfarbe"));
		} else {
			final JSONArray skinColors = generationState.getObj("Rasse").getArr("Hautfarbe");
			newColor = skinColors.getString(DSAUtil.random.nextInt(skinColors.size()));
		}
		skincolor.setValue(newColor);
	}

	@FXML
	private void randomWeight() {
		final JSONObject derivation = generationState.getObj("Rasse").getObj("Gewicht");
		final double deviation = Settings.getSettingIntOrDefault(0, "Heldenerschaffung", "Gewicht:Spanne") / 100.0;
		final boolean obese = generationState.getObj("Held").getObj("Nachteile").containsKey("Fettleibig");
		weight.getValueFactory().setValue(HeroUtil.randomWeight(derivation, size.getValue(), deviation, obese));
	}

	@FXML
	private void toggleWeightTracksSize() {
		weightTracksSize = !weightTracksSize;
		weightTracksSizeButton.setTextFill(weightTracksSize ? Color.BLACK : Color.DARKGRAY);
		if (weightTracksSize) {
			generationState.getObj("Held").getObj("Biografie").removeKey("temporary:weightTracksSize");
			randomWeight();
		} else {
			generationState.getObj("Held").getObj("Biografie").put("temporary:weightTracksSize", false);
		}
	}
}
