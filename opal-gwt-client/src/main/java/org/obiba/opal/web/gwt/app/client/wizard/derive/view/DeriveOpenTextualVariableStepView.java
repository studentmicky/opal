/*******************************************************************************
 * Copyright (c) 2011 OBiBa. All rights reserved.
 *  
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *  
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.gwt.app.client.wizard.derive.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.obiba.opal.web.gwt.app.client.i18n.Translations;
import org.obiba.opal.web.gwt.app.client.ui.RadioGroup;
import org.obiba.opal.web.gwt.app.client.wizard.DefaultWizardStepController;
import org.obiba.opal.web.gwt.app.client.wizard.DefaultWizardStepController.Builder;
import org.obiba.opal.web.gwt.app.client.wizard.derive.helper.OpenTextualVariableDerivationHelper.Method;
import org.obiba.opal.web.gwt.app.client.wizard.derive.presenter.DeriveOpenTextualVariableStepPresenter;
import org.obiba.opal.web.gwt.app.client.workbench.view.WizardStep;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 *
 */
public class DeriveOpenTextualVariableStepView extends ViewImpl implements DeriveOpenTextualVariableStepPresenter.Display {

  @UiTemplate("DeriveOpenTextualVariableStepView.ui.xml")
  interface ViewUiBinder extends UiBinder<Widget, DeriveOpenTextualVariableStepView> {
  }

  private static final ViewUiBinder uiBinder = GWT.create(ViewUiBinder.class);

  private static final Translations translations = GWT.create(Translations.class);

  private final Widget widget;

  // --- Method fields ---
  @UiField
  WizardStep methodStep;

  @UiField
  WizardStep mapStep;

  @UiField(provided = true)
  RadioButton auto;

  @UiField(provided = true)
  RadioButton manual;

  RadioGroup<Method> radioGroup;

  // --- Map fields ---

  @UiField
  DisclosurePanel addPanel;

  @UiField(provided = true)
  SuggestBox value;

  @UiField
  TextBox newValue;

  @UiField
  Button addButton;

  @UiField
  ValueMapGrid valuesMapGrid;

  MultiWordSuggestOracleWithDisplay valueOracle;

  public DeriveOpenTextualVariableStepView() {
    value = new SuggestBox(valueOracle = new MultiWordSuggestOracleWithDisplay());
    auto = new RadioButton(Method.group);
    manual = new RadioButton(Method.group);
    widget = uiBinder.createAndBindUi(this);
    radioGroup = new RadioGroup<Method>();
    radioGroup.addButton(auto, Method.AUTOMATICALLY);
    radioGroup.addButton(manual, Method.MANUAL);
    auto.setValue(true);
    addButton.setText("Add");
  }

  @Override
  public Method getMethod() {
    return radioGroup.getValue();
  }

  @Override
  public Widget asWidget() {
    return widget;
  }

  @Override
  public DefaultWizardStepController.Builder getMethodStepController() {
    return DefaultWizardStepController.Builder.create(methodStep).title(translations.recodeOpenTextualMethodStepTitle());
  }

  @Override
  public Builder getMapStepController() {
    return DefaultWizardStepController.Builder.create(mapStep).title(translations.recodeOpenTextualMapStepTitle());
  }

  @Override
  public void populateValues(List<ValueMapEntry> valueMapEntries, List<String> derivedCategories) {
    value.setText("");
    newValue.setText("");
    addPanel.setOpen(false);
    valuesMapGrid.populate(valueMapEntries, derivedCategories);
  }

  @Override
  public HasValue<String> getValue() {
    return value;
  }

  @Override
  public HasValue<String> getNewValue() {
    return newValue;
  }

  @Override
  public Button getAddButton() {
    return addButton;
  }

  @Override
  public void emptyValueFields() {
    value.setValue(null);
    newValue.setValue(null);
  }

  @Override
  public ValueMapGrid getValueMapGrid() {
    return valuesMapGrid;
  }

  @Override
  public void entryAdded() {
    valuesMapGrid.entryAdded();
  }

  @Override
  public void addValueSuggestion(String value, String frequency) {
    valueOracle.add(value, frequency);
  }

  private static class MultiWordSuggestOracleWithDisplay extends MultiWordSuggestOracle {

    Map<String, String> map = new HashMap<String, String>();

    public void add(String value, String frequency) {
      super.add(value);
      map.put(value, frequency);
    }

    @Override
    protected MultiWordSuggestion createSuggestion(String replacementString, String displayString) {
      return super.createSuggestion(replacementString, displayString + " (" + map.get(replacementString) + ")");
    }

  }

}
