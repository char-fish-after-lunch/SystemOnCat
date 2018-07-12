module SerialPortSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
    err_o, rty_o, sel_i, stb_i, we_i,
    clk_bus, rst_bus, 
    uart_clk, uart_busy, uart_ready, uart_start,
    uart_dat_i, uart_dat_o);

input wire clk_bus;
input wire rst_bus;

// ----------- system bus slave interface ---------


input wire [31:0] dat_i;
output wire [31:0] dat_o;
output wire ack_o;
input wire [31:0] adr_i;
input wire cyc_i;
output wire err_o;
output wire rty_o;
input wire [3:0] sel_i;
input wire stb_i;
input wire we_i;

// ------------------ serial io -----------------
input wire uart_clk;
input wire uart_busy;
input wire uart_ready;
output reg uart_start;
output reg [7:0] uart_dat_o;
input wire [7:0] uart_dat_i;

// ------------------ buffer --------------------
reg [7:0] dat_to_send;
reg [7:0] dat_received;

// ------------------ sliding windows ------------
reg send_phase, o_send_phase; // o for interface
reg recv_phase, o_recv_phase;


initial begin
    send_phase <= 0;
    o_send_phase <= 0;

    recv_phase <= 0;
    o_recv_phase <= 0;

    
    uart_start <= 0;
end

localparam STATE_IDLE = 2'b00,
    STATE_WRITE = 2'b01,
    STATE_READ = 2'b10;

reg [1:0] state;

always @(posedge clk_bus) begin
    case(state)
        STATE_IDLE: begin
            if(cyc_i && stb_i) begin
                if(we_i) begin
                    o_send_phase = ~o_send_phase;
                    dat_to_send <= dat_i[7:0];
                    state <= STATE_WRITE;
                end else begin
                    state <= STATE_READ;
                end
            end
        end
        STATE_READ: begin
            if(recv_phase != o_recv_phase) begin
                o_recv_phase <= recv_phase;
                state <= STATE_IDLE;
            end
        end
        STATE_WRITE: begin
            if(send_phase == o_send_phase) begin
                state <= STATE_IDLE;
            end
        end
    endcase
end

always @(posedge uart_clk) begin
    if(uart_ready) begin
        dat_received <= uart_dat_i;
        recv_phase <= ~o_recv_phase;
    end

    if(state == STATE_WRITE) begin
        if(!uart_busy && send_phase != o_send_phase) begin
            uart_dat_o <= dat_to_send;
            uart_start <= 1;
            send_phase <= o_send_phase;
        end else if(!uart_busy) begin
            uart_start <= 0;
        end
    end
end

assign ack_o = (state == STATE_WRITE && send_phase == o_send_phase) | 
        (state == STATE_READ && recv_phase != o_recv_phase);

assign dat_o = {{24{1'b0}}, dat_received};

endmodule
