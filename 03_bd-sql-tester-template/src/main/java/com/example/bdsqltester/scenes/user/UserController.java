package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
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
import java.util.List;
import java.util.stream.Collectors;

public class UserController {

    @FXML
    private Label assignmentNameLabel;

    @FXML
    private TextArea assignmentInstructionsArea;

    @FXML
    private TextArea userAnswerArea;

    @FXML
    private ListView<Assignment> assignmentListView;

    @FXML
    private Label gradeLabel;

    private Long loggedInUserId;
    private Assignment currentAssignment;
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();

    public void setLoggedInUserId(Long userId) {
        this.loggedInUserId = userId;
        refreshAssignmentList();
    }

    @FXML
    void initialize() {
        assignmentListView.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    currentAssignment = getItem();
                    if (currentAssignment != null) {
                        displayAssignmentDetails(currentAssignment);
                        loadUserGrade(currentAssignment.id);
                    } else {
                        clearAssignmentDetails();
                        gradeLabel.setText("Grade: -");
                    }
                }
            }
        });
    }

    void refreshAssignmentList() {
        assignments.clear();
        try (Connection c = MainDataSource.getConnection()) {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM assignments");
            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load assignments.", e.toString());
        }
        assignmentListView.setItems(assignments);
    }

    void displayAssignmentDetails(Assignment assignment) {
        assignmentNameLabel.setText(assignment.name);
        assignmentInstructionsArea.setText(assignment.instructions);
        userAnswerArea.clear();
    }

    void clearAssignmentDetails() {
        assignmentNameLabel.setText("");
        assignmentInstructionsArea.setText("");
        userAnswerArea.clear();
    }

    void loadUserGrade(Long assignmentId) {
        if (loggedInUserId != null && assignmentId != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String query = "SELECT grade FROM grades WHERE user_id = ? AND assignment_id = ?";
                PreparedStatement stmt = c.prepareStatement(query);
                stmt.setLong(1, loggedInUserId);
                stmt.setLong(2, assignmentId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    gradeLabel.setText("Grade: " + rs.getDouble("grade"));
                } else {
                    gradeLabel.setText("Grade: -");
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to load your grade for this assignment.", e.toString());
                gradeLabel.setText("Grade: Error");
            }
        } else {
            gradeLabel.setText("Grade: -");
        }
    }

    @FXML
    void onTestButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment to test your query.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Query Results");
        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();
        ArrayList<String> headers = new ArrayList<>();

        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(userAnswerArea.getText())) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                final int columnIndex = i - 1;
                String headerText = metaData.getColumnLabel(i);
                headers.add(headerText);
                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);
                column.setCellValueFactory(cellData -> {
                    ArrayList<String> rowData = cellData.getValue();
                    return (rowData != null && columnIndex < rowData.size()) ? new SimpleStringProperty(rowData.get(columnIndex)) : new SimpleStringProperty("");
                });
                column.setPrefWidth(120);
                tableView.getColumns().add(column);
            }

            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    row.add(value != null ? value : "");
                }
                data.add(row);
            }

            if (headers.isEmpty() && data.isEmpty()) {
                showAlert("Query Results", null, "The query executed successfully but returned no data.");
                return;
            }

            tableView.setItems(data);
            StackPane root = new StackPane();
            root.getChildren().add(tableView);
            Scene scene = new Scene(root, 800, 600);
            stage.setScene(scene);
            stage.show();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to execute your query.", e.getMessage());
        }
    }

    @FXML
    void onSubmitButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment to submit your answer.");
            return;
        }

        String userAnswerQuery = userAnswerArea.getText();
        String correctAnswerQuery = currentAssignment.answerKey;
        int grade = calculateGrade(userAnswerQuery, correctAnswerQuery);

        try (Connection c = MainDataSource.getConnection()) {
            String selectExistingGradeQuery = "SELECT grade FROM grades WHERE user_id = ? AND assignment_id = ?";
            PreparedStatement selectStmt = c.prepareStatement(selectExistingGradeQuery);
            selectStmt.setLong(1, loggedInUserId);
            selectStmt.setLong(2, currentAssignment.id);
            ResultSet rs = selectStmt.executeQuery();

            int existingGrade = -1;
            if (rs.next()) {
                existingGrade = rs.getInt("grade");
            }

            if (existingGrade == -1) {
                // Insert new grade
                String insertQuery = "INSERT INTO grades (user_id, assignment_id, grade) VALUES (?, ?, ?)";
                PreparedStatement insertStmt = c.prepareStatement(insertQuery);
                insertStmt.setLong(1, loggedInUserId);
                insertStmt.setLong(2, currentAssignment.id);
                insertStmt.setInt(3, grade);
                int rowsInserted = insertStmt.executeUpdate();
                if (rowsInserted > 0) {
                    showAlert("Submission Successful", null, "Your assignment has been submitted and your grade is: " + grade);
                } else {
                    showAlert("Database Error", "Failed to insert your grade.", null);
                }
            }
            else if (grade > existingGrade) {
                // Update existing grade
                String updateQuery = "UPDATE grades SET grade = ? WHERE user_id = ? AND assignment_id = ?";
                PreparedStatement updateStmt = c.prepareStatement(updateQuery);
                updateStmt.setInt(1, grade);
                updateStmt.setLong(2, loggedInUserId);
                updateStmt.setLong(3, currentAssignment.id);
                int rowsUpdated = updateStmt.executeUpdate();

                if (rowsUpdated > 0) {
                    showAlert("Submission Successful", null, "Your assignment has been submitted and your grade is: " + grade);
                } else {
                    showAlert("Database Error", "Failed to update your grade.", null);
                }
            }
            else
            {
                showAlert("Submission Ignored", null, "Your current grade (" + existingGrade + ") is already higher than or equal to the calculated grade (" + grade + ").");
            }
            loadUserGrade(currentAssignment.id); // Update displayed grade

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to submit your answer or save the grade.", e.getMessage());
        }
    }

    private int calculateGrade(String userAnswerQuery, String correctAnswerQuery) {
        List<String> userResults = executeAndFetch(userAnswerQuery);
        List<String> correctResults = executeAndFetch(correctAnswerQuery);

        // Normalize by trimming whitespace and splitting into individual statements/results
        List<String> normalizedUserResults = userResults.stream()
                .flatMap(s -> List.of(s.split(";")).stream()) // Split by semicolon
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<String> normalizedCorrectResults = correctResults.stream()
                .flatMap(s -> List.of(s.split(";")).stream()) // Split by semicolon
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (normalizedUserResults.equals(normalizedCorrectResults) && !normalizedUserResults.isEmpty()) {
            return 100;
        } else if (normalizedUserResults.stream().sorted().collect(Collectors.toList())
                .equals(normalizedCorrectResults.stream().sorted().collect(Collectors.toList())) && !normalizedUserResults.isEmpty() && normalizedUserResults.size() == normalizedCorrectResults.size()) {
            return 50;
        } else {
            return 0;
        }
    }

    private List<String> executeAndFetch(String sqlQuery) {
        List<String> results = new ArrayList<>();
        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlQuery)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                List<String> rowValues = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    rowValues.add(value != null ? value : "");
                }
                results.add(String.join(",", rowValues)); // Join row values for easier comparison
            }
        } catch (SQLException e) {
            // Log the error, but we'll compare based on potentially empty results
            e.printStackTrace();
        }
        return results;
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
