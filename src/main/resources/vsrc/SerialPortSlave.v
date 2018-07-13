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
reg [7:0] dat_received[15:0];
reg [7:0] dat_to_send[15:0];
reg [3:0] dat_recv_he;
reg [3:0] dat_recv_ta;
reg [3:0] dat_send_he;
reg [3:0] dat_send_ta;
reg [7:0] dat_next_to_send;

wire stall;
reg ack;


// ------------------ sliding windows ------------

localparam STATE_IDLE = 3'b00,
    STATE_WRITE = 3'b01,
    STATE_READ = 3'b10,
    STATE_ERR = 3'b11,
    STATE_READ_COUNT = 3'b100;

reg [2:0] state;
reg [7:0] ans;

initial begin
    uart_start <= 0;

    ack <= 0;

    dat_recv_he <= 0;
    dat_recv_ta <= 0;
    dat_send_he <= 0;
    dat_send_ta <= 0;


    state <= 0;
end


always @(posedge clk_bus) begin
    if(cyc_i && stb_i && !stall) begin
        if(adr_i[0] == 0) begin
            if(we_i) begin
                if(dat_send_ta + 1'b1 == dat_send_he) begin
                    ack <= 0;
                    dat_next_to_send <= dat_i[7:0];
                end else begin
                    dat_to_send[dat_send_ta] <= dat_i[7:0];
                    dat_send_ta <= dat_send_ta + 1'b1;
                    ack <= 1;
                end
                state <= STATE_WRITE;
            end else begin
                if(dat_recv_he != dat_recv_ta) begin
                    ans <= dat_received[dat_recv_he];
                    dat_recv_he <= dat_recv_he + 1;
                    ack <= 1;
                end else
                    ack <= 0;
                state <= STATE_READ;
            end
        end else begin
            if(we_i) begin
                ack <= 0;
                state <= STATE_ERR;
            end else begin
                ack <= 1;
                ans <= {dat_recv_ta - dat_recv_he, 
                    dat_send_he - dat_send_ta - 1'b1};
                state <= STATE_READ_COUNT;
            end
        end
    end else begin
        case(state)
            STATE_READ: begin
                if(ack) begin
                    ack <= 0;
                    state <= STATE_IDLE;
                end else if(dat_recv_he != dat_recv_ta) begin
                    dat_recv_he <= dat_recv_he + 1'b1;
                    ans <= dat_received[dat_recv_he];
                    ack <= 1;
                end
            end
            STATE_WRITE: begin
                if(ack) begin
                    ack <= 0;
                    state <= STATE_IDLE;
                end else if(dat_send_ta + 1'b1 != dat_send_he) begin
                    dat_to_send[dat_send_ta] <= dat_next_to_send;
                    dat_send_ta <= dat_send_ta + 1'b1;
                    ack <= 1;
                end
            end
            STATE_READ_COUNT: begin
                ack <= 0;
                state <= STATE_IDLE;
            end
            STATE_ERR: begin
                state <= STATE_IDLE;
            end
        endcase
    end
end

always @(posedge uart_clk) begin
    if(uart_ready) begin
        dat_received[dat_recv_ta] <= uart_dat_i;
        dat_recv_ta <= dat_recv_ta + 1'b1;
    end

    if(!uart_busy) begin
        if(dat_send_he != dat_send_ta) begin
            uart_dat_o <= dat_to_send[dat_send_he];
            dat_send_he <= dat_send_he + 1'b1;
            uart_start <= 1;
        end else begin
            uart_start <= 0;
        end
    end
end

assign ack_o = ack;
assign err_o = (state == STATE_ERR);
assign rty_o = 0;
assign dat_o = {{24{1'b0}}, ans};

assign stall = (state != STATE_IDLE) & ~ack;
assign stall_o = stall;

endmodule
