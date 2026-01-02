package runner

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"strings"
)

type CommandRequest struct {
	Command string `json:"command"`
}

type CommandResponse struct {
	Success bool    `json:"success"`
	Error   *string `json:"error,omitempty"`
	Result  *string `json:"result,omitempty"`
	Elapsed string  `json:"elapsed,omitempty"`
}

func (r *ActionbaseCommandLineRunner) Start(port string, ready chan<- error) error {
	http.HandleFunc("/api/command", r.handleCommand)
	http.HandleFunc("/health", r.handleHealth)

	addr := ":" + port

	listener, err := net.Listen("tcp", addr)
	if err != nil {
		if ready != nil {
			ready <- err
		}
		return err
	}

	fmt.Printf("Started as server mode on http://localhost%s/api/command\n", addr)

	if ready != nil {
		ready <- nil
	}

	return http.Serve(listener, nil)
}

func (r *ActionbaseCommandLineRunner) handleHealth(w http.ResponseWriter, req *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(map[string]string{"status": "ok"}); err != nil {
		return
	}
}

func (r *ActionbaseCommandLineRunner) handleCommand(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodPost {
		fmt.Println("Method not allowed")
		buildResponse(w, http.StatusMethodNotAllowed, nil)
		return
	}

	var request CommandRequest
	if err := json.NewDecoder(req.Body).Decode(&request); err != nil {
		errorMessage := fmt.Sprintf("Invalid request: %v", err)
		buildResponse(
			w,
			http.StatusBadRequest,
			&CommandResponse{
				Success: false,
				Error:   &errorMessage,
			})
		return
	}

	if request.Command == "" {
		errorMessage := "Command is required"

		buildResponse(
			w,
			http.StatusBadRequest,
			&CommandResponse{
				Success: false,
				Error:   &errorMessage,
			})
		return
	}

	command := strings.TrimSpace(request.Command)

	r.ReadLine.Terminal.Print(fmt.Sprintf("\n\033[38;5;208m%s>\033[0m %s\n", r.BuildPrompt(), command))
	result, elapsed := r.RunCommand(command)

	var response CommandResponse
	if result == nil {
		errorMessage := "Unsupported command"
		response = CommandResponse{
			Success: false,
			Elapsed: fmt.Sprintf("%.4f seconds", elapsed),
			Error:   &errorMessage,
		}
	} else {
		response = CommandResponse{
			Success: result.IsSuccess,
			Elapsed: fmt.Sprintf("%.4f seconds", elapsed),
			Result:  result.Result,
			Error:   result.ErrorMessage,
		}
	}

	statusCode := http.StatusOK
	if !response.Success {
		statusCode = http.StatusInternalServerError
	}

	buildResponse(w, statusCode, &response)

	r.ReadLine.SetPrompt(fmt.Sprintf("\033[34m%s%s \033[0m", r.BuildPrompt(), defaultPrompt))
	r.ReadLine.Terminal.Print(fmt.Sprintf("\033[34m%s>\033[0m ", r.BuildPrompt()))
}

func buildResponse(w http.ResponseWriter, httpStatus int, response *CommandResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(httpStatus)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		fmt.Println("Failed to encode response:", err)
	}
}
