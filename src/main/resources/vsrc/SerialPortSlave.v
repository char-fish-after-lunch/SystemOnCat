module SerialPortSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
    err_o, rty_o, sel_i, stb_i, we_i, stall_o,
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
output wire stall_o;

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

wire stall;
reg ack;


// ------------------ sliding windows ------------
reg send_phase, o_send_phase; // o for interface
reg recv_phase, o_recv_phase;


initial begin
    send_phase <= 0;
    o_send_phase <= 0;

    recv_phase <= 0;
    o_recv_phase <= 0;

    
    uart_start <= 0;

    ack <= 0;
end

localparam STATE_IDLE = 2'b00,
    STATE_WRITE = 2'b01,
    STATE_READ = 2'b10;

reg [1:0] state;

always @(posedge clk_bus) begin
    if(cyc_i && stb_i && !stall) begin
        if(we_i) begin
            ack <= 0;
            o_send_phase = ~o_send_phase;
            dat_to_send <= dat_i[7:0];
            state <= STATE_WRITE;
        end else begin
            if(o_recv_phase != recv_phase) begin
                o_recv_phase = ~o_recv_phase;
                ack <= 1;
            end else
                ack <= 0;
            state <= STATE_READ;
        end
    end else begin
        case(state)
            STATE_READ: begin
                if(ack) begin
                    ack <= 0;
                    state <= STATE_IDLE;
                end else if(recv_phase != o_recv_phase) begin
                    o_recv_phase <= recv_phase;
                    ack <= 1;
                end
            end
            STATE_WRITE: begin
                if(ack) begin
                    ack <= 0;
                    state <= STATE_IDLE;
                end else if(send_phase == o_send_phase) begin
                    ack <= 1;
                end
            end
        endcase
    end
end

always @(posedge uart_clk) begin
    if(uart_ready) begin
        dat_received <= uart_dat_i;
        recv_phase <= ~o_recv_phase;
    end

    if(state == STATE_WRITE && !ack && !uart_busy) begin
        if(send_phase != o_send_phase) begin
            uart_dat_o <= dat_to_send;
            uart_start <= 1;
            send_phase <= o_send_phase;
        end else begin
            uart_start <= 0;
        end
    end
end

// assign ack = (state == STATE_WRITE && send_phase == o_send_phase) | 
//         (state == STATE_READ && recv_phase != o_recv_phase);
assign ack_o = ack;
assign err_o = 0;
assign rty_o = 0;
assign dat_o = {{24{1'b0}}, dat_received};

assign stall = (state != STATE_IDLE) & ~ack;
assign stall_o = stall;

endmodule
