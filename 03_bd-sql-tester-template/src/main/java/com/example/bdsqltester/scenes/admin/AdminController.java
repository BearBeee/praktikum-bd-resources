package com.example.bdsqltester.scenes.admin;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import com.example.bdsqltester.dtos.Grade;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;

public class AdminController {

    @FXML
    private TextArea answerKeyField;

    @FXML
    private ListView<Assignment> assignmentList = new ListView<>();

    @FXML
    private TextField idField;

    @FXML
    private TextArea instructionsField;

    @FXML
    private TextField nameField;

    @FXML
    private TableView<GradeDisplay> gradesTableView;

    @FXML
    private TableColumn<GradeDisplay, String> usernameColumn;

    @FXML
    private TableColumn<GradeDisplay, Double> gradeColumn;

    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private final ObservableList<GradeDisplay> grades = FXCollections.observableArrayList();
    private Assignment selectedAssignmentForGrades;

    // Helper class to display username with grade
    public static class GradeDisplay {
        private final SimpleStringProperty username;
        private final double grade;

        public GradeDisplay(String username, double grade) {
            this.username = new SimpleStringProperty(username);
            this.grade = grade;
        }

        public String getUsername() {
            return username.get();
        }

        public double getGrade() {
            return grade;
        }

        public SimpleStringProperty usernameProperty() {
            return username;
        }
    }

    @FXML
    void initialize() {
        // Set idField to read-only
        idField.setEditable(false);
        idField.setMouseTransparent(true);
        idField.setFocusTraversable(false);

        // Populate the ListView with assignment names
        refreshAssignmentList();

        assignmentList.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                }
            }

            // Bind the onAssignmentSelected method to the ListView
            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    selectedAssignmentForGrades = getItem();
                    onAssignmentSelected(selectedAssignmentForGrades);
                    loadGradesForAssignment(selectedAssignmentForGrades);
                }
            }
        });

        // Configure the Grades TableView columns
        usernameColumn.setCellValueFactory(cellData -> cellData.getValue().usernameProperty());
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        gradesTableView.setItems(grades);
    }

    void refreshAssignmentList() {
        // Clear the current list
        assignments.clear();

        // Re-populate the ListView with assignment names
        try (Connection c = MainDataSource.getConnection()) {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM assignments");

            while (rs.next()) {
                // Create a new assignment object
                assignments.add(new Assignment(rs));
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Database Error");
            alert.setContentText(e.toString());
        }

        // Set the ListView to display assignment names
        assignmentList.setItems(assignments);

        // Set currently selected to the id inside the id field
        try {
            if (!idField.getText().isEmpty()) {
                long id = Long.parseLong(idField.getText());
                for (Assignment assignment : assignments) {
                    if (assignment.id == id) {
                        assignmentList.getSelectionModel().select(assignment);
                        selectedAssignmentForGrades = assignment;
                        loadGradesForAssignment(selectedAssignmentForGrades);
                        break;
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Ignore, idField is empty
        }
    }

    void onAssignmentSelected(Assignment assignment) {
        // Set the id field
        if (assignment != null) {
            idField.setText(String.valueOf(assignment.id));
            nameField.setText(assignment.name);
            instructionsField.setText(assignment.instructions);
            answerKeyField.setText(assignment.answerKey);
        } else {
            idField.clear();
            nameField.clear();
            instructionsField.clear();
            answerKeyField.clear();
        }
    }

    @FXML
    void onNewAssignmentClick(ActionEvent event) {
        // Clear the contents of the id field
        idField.clear();

        // Clear the contents of all text fields
        nameField.clear();
        instructionsField.clear();
        answerKeyField.clear();
        assignmentList.getSelectionModel().clearSelection();
        grades.clear(); // Clear grades table when creating a new assignment
    }

    @FXML
    void onDeleteAssignmentClick(ActionEvent event) {
        Assignment selectedAssignment = assignmentList.getSelectionModel().getSelectedItem();

        if (selectedAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment to delete.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Delete Assignment");
        confirmation.setContentText("Are you sure you want to delete the assignment: " + selectedAssignment.name + "? This will also delete any associated grades.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection c = MainDataSource.getConnection()) {
                    // Delete associated grades first (optional, but recommended for data integrity)
                    PreparedStatement deleteGradesStmt = c.prepareStatement("DELETE FROM grades WHERE assignment_id = ?");
                    deleteGradesStmt.setLong(1, selectedAssignment.id);
                    deleteGradesStmt.executeUpdate();

                    // Delete the assignment
                    PreparedStatement deleteAssignmentStmt = c.prepareStatement("DELETE FROM assignments WHERE id = ?");
                    deleteAssignmentStmt.setLong(1, selectedAssignment.id);
                    int rowsAffected = deleteAssignmentStmt.executeUpdate();

                    if (rowsAffected > 0) {
                        showAlert("Success", "Assignment Deleted", "Assignment '" + selectedAssignment.name + "' has been successfully deleted.");
                        refreshAssignmentList(); // Refresh the list
                        // Clear the details pane if the deleted assignment was selected
                        if (selectedAssignmentForGrades != null && selectedAssignmentForGrades.id == selectedAssignment.id) {
                            onAssignmentSelected(null);
                            grades.clear();
                        }
                    } else {
                        showAlert("Error", "Deletion Failed", "Failed to delete assignment '" + selectedAssignment.name + "'.");
                    }
                } catch (SQLException e) {
                    showAlert("Database Error", "Error deleting assignment.", e.toString());
                }
            }
        });
    }

    @FXML
    void onSaveClick(ActionEvent event) {
        String name = nameField.getText();
        String instructions = instructionsField.getText();
        String answerKey = answerKeyField.getText();

        if (name.isEmpty() || instructions.isEmpty() || answerKey.isEmpty()) {
            showAlert("Warning", "Empty Fields", "Please fill in all the fields to save the assignment.");
            return;
        }

        // If id is set, update, else insert
        try (Connection c = MainDataSource.getConnection()) {
            if (idField.getText().isEmpty()) {
                // Insert new assignment
                PreparedStatement stmt = c.prepareStatement("INSERT INTO assignments (name, instructions, answer_key) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, name);
                stmt.setString(2, instructions);
                stmt.setString(3, answerKey);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    // Get generated id, update idField
                    idField.setText(String.valueOf(rs.getLong(1)));
                    refreshAssignmentList(); // Refresh list to select the new assignment
                }
            } else {
                // Update existing assignment
                PreparedStatement stmt = c.prepareStatement("UPDATE assignments SET name = ?, instructions = ?, answer_key = ? WHERE id = ?");
                stmt.setString(1, name);
                stmt.setString(2, instructions);
                stmt.setString(3, answerKey);
                stmt.setInt(4, Integer.parseInt(idField.getText()));
                stmt.executeUpdate();
                refreshAssignmentList(); // Refresh list to update the selected assignment
            }
        } catch (Exception e) {
            showAlert("Database Error", "Failed to save assignment.", e.toString());
        }
    }

    @FXML
    void onShowGradesClick(ActionEvent event) {
        // Make sure an assignment is selected
        if (selectedAssignmentForGrades == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("No Assignment Selected");
            alert.setContentText("Please select an assignment to view grades.");
            alert.showAndWait();
            return;
        }
        loadGradesForAssignment(selectedAssignmentForGrades);
    }

    void loadGradesForAssignment(Assignment assignment) {
        grades.clear();
        if (assignment != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String query = "SELECT u.username, g.grade FROM grades g JOIN users u ON g.user_id = u.id WHERE g.assignment_id = ?";
                PreparedStatement stmt = c.prepareStatement(query);
                stmt.setLong(1, assignment.id);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    grades.add(new GradeDisplay(rs.getString("username"), rs.getDouble("grade")));
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to load grades.", e.toString());
            }
        }
    }

    @FXML
    void onTestButtonClick(ActionEvent event) {
        // Display a window containing the results of the query in the answer key field.

        // Create a new window/stage
        Stage stage = new Stage();
        stage.setTitle("Answer Key Test Results");

        // Display in a table view.
        TableView<ArrayList<String>> tableView = new TableView<>();

        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();
        ArrayList<String> headers = new ArrayList<>(); // To check if any columns were returned

        // Use try-with-resources for automatic closing of Connection, Statement, ResultSet
        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(answerKeyField.getText())) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 1. Get Headers and Create Table Columns
            for (int i = 1; i <= columnCount; i++) {
                final int columnIndex = i - 1; // Need final variable for lambda (0-based index for ArrayList)
                String headerText = metaData.getColumnLabel(i); // Use label for potential aliases
                headers.add(headerText); // Keep track of headers

                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);

                // Define how to get the cell value for this column from an ArrayList<String> row object
                column.setCellValueFactory(cellData -> {
                    ArrayList<String> rowData = cellData.getValue();
                    // Ensure rowData exists and the index is valid before accessing
                    if (rowData != null && columnIndex < rowData.size()) {
                        return new SimpleStringProperty(rowData.get(columnIndex));
                    } else {
                        return new SimpleStringProperty(""); // Should not happen with current logic, but safe fallback
                    }
                });
                column.setPrefWidth(120); // Optional: set a preferred width
                tableView.getColumns().add(column);
            }

            // 2. Get Data Rows
            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    // Retrieve all data as String. Handle NULLs gracefully.
                    String value = rs.getString(i);
                    row.add(value != null ? value : ""); // Add empty string for SQL NULL
                }
                data.add(row);
            }

            // 3. Check if any results (headers or data) were actually returned
            if (headers.isEmpty() && data.isEmpty()) {
                // Handle case where query might be valid but returns no results
                Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
                infoAlert.setTitle("Query Results");
                infoAlert.setHeaderText(null);
                infoAlert.setContentText("The query executed successfully but returned no data.");
                infoAlert.showAndWait();
                return; // Exit the method, don't show the empty table window
            }

            // 4. Set the data items into the table
            tableView.setItems(data);

            // 5. Create layout and scene
            StackPane root = new StackPane();
            root.getChildren().add(tableView);
            Scene scene = new Scene(root, 800, 600); // Adjust size as needed

            // 6. Set scene and show stage
            stage.setScene(scene);
            stage.show();

        } catch (SQLException e) {
            // Log the error and show an alert to the user
            e.printStackTrace(); // Print stack trace to console/log for debugging
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Database Error");
            errorAlert.setHeaderText("Failed to execute answer key query.");
            errorAlert.setContentText("SQL Error: " + e.getMessage());
            errorAlert.showAndWait();
        } catch (Exception e) {
            // Catch other potential exceptions
            e.printStackTrace();
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error");
            errorAlert.setHeaderText("An unexpected error occurred.");
            errorAlert.setContentText(e.getMessage());
            errorAlert.showAndWait();
        }
    } // End of onTestButtonClick method

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}