package com.minimartpos.controller.cashier;

import com.minimartpos.model.Product;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class WeightInputController {

    @FXML private Label productNameLabel;
    @FXML private TextField weightField;
    @FXML private Label unitLabel;
    @FXML private Label pricePerUnitLabel;
    @FXML private Label totalPriceLabel;

    private BigDecimal weightValue = BigDecimal.ZERO;
    private Product product;
    private boolean confirmed = false;

    public void initialize() {
        weightField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) {
                weightField.setText(oldValue);
            } else {
                updateTotal();
            }
        });
    }

    public void setProduct(Product product) {
        this.product = product;
        productNameLabel.setText(product.getName());
        unitLabel.setText(product.getWeightUnit() != null ? product.getWeightUnit() : "kg");
        pricePerUnitLabel.setText("Rs. " + product.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toString());
        
        BigDecimal defaultWeight = product.getDefaultWeight();
        if (defaultWeight == null || defaultWeight.compareTo(BigDecimal.ZERO) == 0) {
            defaultWeight = BigDecimal.ONE;
        }
        weightField.setText(defaultWeight.toString());
        weightField.selectAll();
        updateTotal();
    }

    private void updateTotal() {
        try {
            String text = weightField.getText();
            if (text.isEmpty()) {
                totalPriceLabel.setText("Rs. 0.00");
                return;
            }
            BigDecimal weight = new BigDecimal(text);
            BigDecimal total = weight.multiply(product.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP);
            totalPriceLabel.setText("Rs. " + total.toString());
        } catch (NumberFormatException e) {
            totalPriceLabel.setText("Rs. 0.00");
        }
    }

    @FXML
    private void setWeightShortcut(ActionEvent event) {
        Button btn = (Button) event.getSource();
        weightField.setText((String) btn.getUserData());
        weightField.requestFocus();
        weightField.selectAll();
        updateTotal();
    }

    @FXML
    private void confirm() {
        try {
            weightValue = new BigDecimal(weightField.getText());
            if (weightValue.compareTo(BigDecimal.ZERO) <= 0) {
                // TODO: Validation error
                return;
            }
            confirmed = true;
            closeStage();
        } catch (NumberFormatException e) {
            // TODO: Error
        }
    }

    @FXML
    private void cancel() {
        confirmed = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) weightField.getScene().getWindow();
        stage.close();
    }

    public boolean isConfirmed() { return confirmed; }
    public BigDecimal getWeightValue() { return weightValue; }
}
