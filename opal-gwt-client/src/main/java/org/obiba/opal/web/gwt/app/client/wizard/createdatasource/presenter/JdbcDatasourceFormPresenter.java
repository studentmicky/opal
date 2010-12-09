/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.gwt.app.client.wizard.createdatasource.presenter;

import java.util.ArrayList;
import java.util.List;

import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.place.Place;
import net.customware.gwt.presenter.client.place.PlaceRequest;

import org.obiba.opal.web.gwt.app.client.validator.RequiredOptionValidator;
import org.obiba.opal.web.gwt.app.client.validator.RequiredTextValidator;
import org.obiba.opal.web.gwt.app.client.validator.ValidatableWidgetPresenter;
import org.obiba.opal.web.gwt.rest.client.ResourceCallback;
import org.obiba.opal.web.gwt.rest.client.ResourceRequestBuilderFactory;
import org.obiba.opal.web.model.client.magma.DatasourceFactoryDto;
import org.obiba.opal.web.model.client.magma.JdbcDatasourceFactoryDto;
import org.obiba.opal.web.model.client.magma.JdbcDatasourceSettingsDto;
import org.obiba.opal.web.model.client.opal.JdbcDriverDto;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HasValue;
import com.google.inject.Inject;

/**
 *
 */
public class JdbcDatasourceFormPresenter extends ValidatableWidgetPresenter<JdbcDatasourceFormPresenter.Display> implements DatasourceFormPresenter {

  //
  // Constructors
  //

  @SuppressWarnings("unchecked")
  @Inject
  public JdbcDatasourceFormPresenter(final Display display, final EventBus eventBus) {
    super(display, eventBus);

    addValidator(new RequiredTextValidator(getDisplay().getUrl(), "UrlRequired"));
    addValidator(new RequiredTextValidator(getDisplay().getUsername(), "UsernameRequired"));
    addValidator(new RequiredTextValidator(getDisplay().getPassword(), "PasswordRequired"));
    addValidator(new RequiredOptionValidator(RequiredOptionValidator.asSet(getDisplay().getUseMetadataTablesOption(), getDisplay().getDoNotUseMetadataTablesOption()), "MustIndicateWhetherJdbcDatasourceShouldUseMetadataTables"));
  }

  //
  // WidgetPresenter Methods
  //

  @Override
  protected void onBind() {
    setJdbcDrivers();
  }

  @Override
  protected void onUnbind() {
  }

  @Override
  public void revealDisplay() {
  }

  @Override
  public void refreshDisplay() {
  }

  @Override
  public Place getPlace() {
    return null;
  }

  @Override
  protected void onPlaceRequest(PlaceRequest request) {
  }

  //
  // DatasourceFormPresenter Methods
  //

  public DatasourceFactoryDto getDatasourceFactory() {
    JdbcDatasourceFactoryDto extensionDto = JdbcDatasourceFactoryDto.create();
    extensionDto.setDriver(getDisplay().getDriver().getText());
    extensionDto.setUrl(getDisplay().getUrl().getText());
    extensionDto.setUsername(getDisplay().getUsername().getText());
    extensionDto.setPassword(getDisplay().getPassword().getText());
    extensionDto.setSettings(getSettings());

    DatasourceFactoryDto dto = DatasourceFactoryDto.create();
    dto.setExtension(JdbcDatasourceFactoryDto.DatasourceFactoryDtoExtensions.params, extensionDto);

    return dto;
  }

  public boolean isForType(String type) {
    return type.equalsIgnoreCase("jdbc");
  }

  //
  // Methods
  //

  private JdbcDatasourceSettingsDto getSettings() {
    JdbcDatasourceSettingsDto settingsDto = JdbcDatasourceSettingsDto.create();
    settingsDto.setDefaultEntityType("Participant");
    settingsDto.setUseMetadataTables(getDisplay().getUseMetadataTablesOption().getValue());

    if(getDisplay().getDefaultCreatedTimestampColumnName().getText().trim().length() != 0) {
      settingsDto.setDefaultCreatedTimestampColumnName(getDisplay().getDefaultCreatedTimestampColumnName().getText());
    }

    if(getDisplay().getDefaultUpdatedTimestampColumnName().getText().trim().length() != 0) {
      settingsDto.setDefaultUpdatedTimestampColumnName(getDisplay().getDefaultUpdatedTimestampColumnName().getText());
    }

    return settingsDto;
  }

  private void setJdbcDrivers() {
    ResourceRequestBuilderFactory.<JsArray<JdbcDriverDto>> newBuilder().forResource("/system/jdbcDrivers").get().withCallback(new ResourceCallback<JsArray<JdbcDriverDto>>() {

      @Override
      public void onResource(Response response, JsArray<JdbcDriverDto> drivers) {
        List<JdbcDriverDto> driverList = new ArrayList<JdbcDriverDto>();
        for(int i = 0; i < drivers.length(); i++) {
          driverList.add(drivers.get(i));
        }
        getDisplay().setJdbcDrivers(driverList);
      }
    }).send();
  }

  //
  // Interfaces and Inner Classes
  //

  public interface Display extends DatasourceFormPresenter.Display {

    HasText getDriver();

    HasText getUrl();

    HasText getUsername();

    HasText getPassword();

    HasValue<Boolean> getUseMetadataTablesOption();

    HasValue<Boolean> getDoNotUseMetadataTablesOption();

    HasText getDefaultCreatedTimestampColumnName();

    HasText getDefaultUpdatedTimestampColumnName();

    void setJdbcDrivers(List<JdbcDriverDto> drivers);
  }

  @Override
  public boolean validateFormData() {
    return validate();
  }

  @Override
  public void clearForm() {
    getDisplay().getUrl().setText("");
    getDisplay().getUsername().setText("");
    getDisplay().getPassword().setText("");
    getDisplay().getDefaultCreatedTimestampColumnName().setText("");
    getDisplay().getDefaultUpdatedTimestampColumnName().setText("");
    getDisplay().getDoNotUseMetadataTablesOption().setValue(true);
    getDisplay().getUseMetadataTablesOption().setValue(false);
    getDisplay().getDriver().setText("");
  }
}
