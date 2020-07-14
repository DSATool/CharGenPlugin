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
package chargen;

import chargen.ui.CharGenController;
import dsatool.gui.Main;
import dsatool.plugins.Plugin;
import dsatool.resources.Settings;
import dsatool.settings.IntegerSetting;

/**
 * A plugin for character generation
 *
 * @author Dominik Helm
 */
public class CharGen extends Plugin {

	private CharGenController controller;

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#getPluginName()
	 */
	@Override
	public String getPluginName() {
		return "CharGen";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#initialize()
	 */
	@Override
	public void initialize() {
		Main.addDetachableToolComposite("Helden", "Erschaffung", 900, 800, () -> {
			controller = new CharGenController();
			getNotifications = true;
			return controller.getRoot();
		});
		Settings.addSetting(new IntegerSetting("GP", 110, 0, 999, "Heldenerschaffung", "GP"));
		Settings.addSetting(new IntegerSetting("GP für Eigenschaften", 100, 0, 999, "Heldenerschaffung", "GP für Eigenschaften"));
		Settings.addSetting(new IntegerSetting("Minimum für Eigenschaften", 8, 0, 98, "Heldenerschaffung", "Minimum für Eigenschaften"));
		Settings.addSetting(new IntegerSetting("Maximum für Eigenschaften", 14, 1, 99, "Heldenerschaffung", "Maximum für Eigenschaften"));
		Settings.addSetting(new IntegerSetting("GP aus Nachteilen", 50, 0, 999, "Heldenerschaffung", "GP aus Nachteilen"));
		Settings.addSetting(new IntegerSetting("GP aus Schlechten Eigenschaften", 30, 0, 999, "Heldenerschaffung", "GP aus Schlechten Eigenschaften"));
		Settings.addSetting(new IntegerSetting("Gewicht: max. Abweichung in Prozent", 15, 0, 999, "Heldenerschaffung", "Gewicht:Spanne"));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#load()
	 */
	@Override
	public void load() {
		controller.init();
	}
}
