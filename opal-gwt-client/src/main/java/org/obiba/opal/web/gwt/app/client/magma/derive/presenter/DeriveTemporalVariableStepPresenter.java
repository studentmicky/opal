/*
 * Copyright (c) 2018 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.opal.web.gwt.app.client.magma.derive.presenter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.obiba.opal.web.gwt.app.client.event.NotificationEvent;
import org.obiba.opal.web.gwt.app.client.magma.derive.helper.DerivationHelper;
import org.obiba.opal.web.gwt.app.client.magma.derive.helper.TemporalVariableDerivationHelper;
import org.obiba.opal.web.gwt.app.client.magma.derive.view.ValueMapEntry;
import org.obiba.opal.web.gwt.app.client.ui.wizard.DefaultWizardStepController;
import org.obiba.opal.web.gwt.app.client.ui.wizard.WizardStepController;
import org.obiba.opal.web.gwt.app.client.ui.wizard.WizardStepController.StepInHandler;
import org.obiba.opal.web.gwt.app.client.validator.ValidationHandler;
import org.obiba.opal.web.model.client.magma.TableDto;
import org.obiba.opal.web.model.client.magma.VariableDto;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

/**
 *
 */
public class DeriveTemporalVariableStepPresenter
    extends DerivationPresenter<DeriveTemporalVariableStepPresenter.Display> implements DerivationUiHandlers {

  private TemporalVariableDerivationHelper derivationHelper;

  @Inject
  public DeriveTemporalVariableStepPresenter(EventBus eventBus, Display view) {
    super(eventBus, view);
  }

  @Override
  void initialize(TableDto originalTable, TableDto destinationTable, VariableDto originalVariable,
      VariableDto derivedVariable) {
    super.initialize(originalTable, destinationTable, originalVariable, derivedVariable);
    getView().setTimeType(originalVariable.getValueType());
    getView().setUiHandlers(this);
  }

  @Override
  List<DefaultWizardStepController.Builder> getWizardStepBuilders(WizardStepController.StepInHandler stepInHandler) {
    List<DefaultWizardStepController.Builder> stepBuilders = new ArrayList<DefaultWizardStepController.Builder>();
    stepBuilders.add(getView().getMethodStepController() //
        .onStepIn(stepInHandler) //
        .onValidate(new ValidationHandler() {
          @Override
          public boolean validate() {
            // validate derivation method selected
            if(!getView().spanSelected() && !getView().rangeSelected()) {
              fireEvent(NotificationEvent.newBuilder().error("SelectDerivationMethod").build());
              return false;
            }

            Date from = getView().getFromDate();
            Date to = getView().getToDate();
            if(from != null && to != null && !from.after(to)) return true;
            fireEvent(NotificationEvent.newBuilder().error("DatesRangeInvalid").build());
            return false;
          }
        }));
    stepBuilders.add(getView().getMapStepController() //
        .onStepIn(new DeriveTemporalVariableMapStepInHandler()) //
        .onValidate(new MapStepValidationHandler() {

          @Override
          public List<String> getErrors() {
            return derivationHelper.getMapStepErrors();
          }

          @Override
          public List<String> getWarnings() {
            return derivationHelper.getMapStepWarnings();
          }
        }));
    return stepBuilders;
  }

  @Override
  public void generateDerivedVariable() {
    setDerivedVariable(derivationHelper.getDerivedVariable());
  }

  @Override
  public void onMethodChange() {
    // We are in a "derived to" wizard
    if(getDestinationTable() == null) {
      // Reinitialize the destination variable
      setDerivedVariable(null);
    }
  }

  private final class DeriveTemporalVariableMapStepInHandler implements StepInHandler {
    @Override
    public void onStepIn() {
      // do not re-populate if group method selection has not changed
      if(derivationHelper == null //
          || !derivationHelper.getGroupMethod().toString().equalsIgnoreCase(getView().getGroupMethod()) //
          || !derivationHelper.getFromDate().equals(getView().getFromDate()) //
          || !derivationHelper.getToDate().equals(getView().getToDate())) {
        derivationHelper = new TemporalVariableDerivationHelper(getOriginalVariable(), getDerivedVariable(),
            getView().getGroupMethod(), getView().getFromDate(), getView().getToDate());
        getView().populateValues(derivationHelper.getValueMapEntries(),
            DerivationHelper.getDestinationCategories(getDerivedVariable()));
      }
    }
  }

  //
  // Interfaces
  //

  public interface Display extends View, HasUiHandlers<DerivationUiHandlers> {

    DefaultWizardStepController.Builder getMethodStepController();

    void setTimeType(String valueType);

    DefaultWizardStepController.Builder getMapStepController();

    String getGroupMethod();

    void populateValues(List<ValueMapEntry> valuesMap, List<String> derivedCategories);

    Date getFromDate();

    Date getToDate();

    boolean spanSelected();

    boolean rangeSelected();
  }

}
