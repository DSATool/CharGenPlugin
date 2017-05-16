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

import dsa41basis.hero.Talent;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jsonant.value.JSONObject;

public class ProxyTalent extends Talent {
	private Talent wrapped;
	private int change = 0;
	private final IntegerProperty wrappedValue;
	private final IntegerProperty target;

	public ProxyTalent(String name, Talent wrapped, JSONObject actualGroup, IntegerProperty target) {
		super(name, null, wrapped == null ? new JSONObject(null) : wrapped.getTalent(), wrapped == null ? new JSONObject(null) : wrapped.getActual(),
				actualGroup);
		this.wrapped = wrapped;
		wrappedValue = new SimpleIntegerProperty();
		this.target = target;
		if (wrapped == null) {
			wrappedValue.set(0);
		} else {
			wrappedValue.bind(wrapped.valueProperty());
		}
	}

	public void changeTalent(Talent newTalent, JSONObject newActualGroup) {
		wrappedValue.unbind();
		wrapped = newTalent;
		wrappedValue.bind(wrapped.valueProperty());
		talent = wrapped.getTalent();
		actual = wrapped.getActual();
		actualGroup = newActualGroup;
	}

	@Override
	public int getValue() {
		return change;
	}

	@Override
	public void setValue(int newVal) {
		newVal = newVal == Integer.MIN_VALUE ? 0 : newVal;
		target.setValue(target.getValue() + newVal - change);
		change = newVal;
	}

	@Override
	public IntegerProperty valueProperty() {
		return wrappedValue;
	}

}
