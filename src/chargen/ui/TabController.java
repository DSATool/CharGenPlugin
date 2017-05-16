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
package chargen.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import jsonant.value.JSONObject;

public abstract class TabController {
	final protected JSONObject generationState;
	final protected IntegerProperty gp;

	final protected BooleanProperty canContinue = new SimpleBooleanProperty(false);

	protected TabController(JSONObject generationState, IntegerProperty gp) {
		this.generationState = generationState;
		this.gp = gp;
	}

	public abstract void activate(boolean forward);

	protected Tab addTab(TabPane tabPane, String text, Node node) {
		final Tab tab = new Tab(text);
		tab.setContent(node);
		tab.setClosable(false);
		tab.setDisable(true);
		tabPane.getTabs().add(tab);
		return tab;
	}

	public ReadOnlyBooleanProperty canContinue() {
		return canContinue;
	}

	public abstract void deactivate(boolean forward);
}
