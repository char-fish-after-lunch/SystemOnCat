module FlashSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
    err_o, rty_o, sel_i, stb_i, we_i, stall_o,
    clk_bus, rst_bus,
    flash_a, flash_d, flash_rp_n, flash_vpen,
    flash_ce_n, flash_we_n, flash_oe_n, flash_byte_n, flash_clk);

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

// ------------------ flash io ---------------

input wire flash_clk;

output reg [22:0]flash_a; // address
inout  wire [15:0]flash_d; // data
output wire flash_rp_n; // reset
output wire flash_vpen; // protection enabled
output wire flash_ce_n; // chip enabled
output wire flash_oe_n; // output enabled
output wire flash_we_n; // write enabled
output wire flash_byte_n; // 0 = 8x mode, 1 = 16x mode


localparam STATE_IDLE = 3'b00;
localparam STATE_ADR_WRITE = 3'b001;
localparam STATE_DAT_READ = 3'b010;
localparam STATE_DAT_WRITE = 3'b100;
localparam STATE_ERR = 3'b101;

reg [2:0] state;
// sliding window
reg read_phase, o_read_phase;
reg [7:0] dat_read;
reg busy;
wire stall, err;
reg ack;

initial begin
    state <= STATE_IDLE;
    read_phase <= 0;
    o_read_phase <= 0;
    busy <= 0;

    ack <= 0;
end

always @(posedge clk_bus) begin
    if(cyc_i && stb_i && !stall) begin
        if(we_i) begin
            if(adr_i[2] == 0) begin
                // DAT_WRITE : not implemented
                ack <= 0;
                state <= STATE_ERR;
            end else begin
                // ADR_WRITE
                ack <= 1;
                flash_a <= dat_i[22:0];
                state <= STATE_ADR_WRITE;
            end
        end else begin
            ack <= 0;
            if(adr_i[2] == 0) begin
                // DAT_READ
                state <= STATE_DAT_READ;
            end else begin
                // ADR_READ
                // considered invalid
                state <= STATE_ERR;
            end
        end
    end else begin
        case(state)
            STATE_ADR_WRITE: begin
                ack <= 0;
                state <= STATE_IDLE;
            end
            STATE_DAT_READ: begin
                if(ack) begin
                    ack <= 0;
                    state <= STATE_IDLE;
                end else if(o_read_phase != read_phase) begin
                    o_read_phase <= read_phase;
                    ack <= 1;
                end
            end
            STATE_DAT_WRITE: begin
                // not implemented
                state <= STATE_IDLE;
            end
            STATE_ERR: begin
                state <= STATE_IDLE;
            end
        endcase
    end
end

always @(posedge flash_clk) begin
    if(state == STATE_DAT_READ && !ack && o_read_phase == read_phase) begin
        if(busy) begin
            // finished
            busy <= 1'b0;
            dat_read <= flash_d[7:0];
            read_phase = ~read_phase;
        end else begin
            // start to read
            busy <= 1'b1;
        end
    end
end

assign flash_byte_n = 1'b0;
assign flash_d = {16{1'bZ}}; // only receive data from device
assign flash_vpen = 1'b0;
assign flash_rp_n = 1'b1;

// assign ack = (state == STATE_ADR_WRITE) | (state == STATE_DAT_READ && o_read_phase != read_phase);
assign err = (state == STATE_ERR);
assign ack_o = ack;
assign err_o = err;
assign rty_o = 1'b0;
assign dat_o = {{24{1'b0}}, dat_read};
assign stall = (state != STATE_IDLE) & ~ack & ~err;
assign stall_o = stall;

assign flash_ce_n = ~busy;
assign flash_we_n = state != STATE_DAT_WRITE;
assign flash_oe_n = state != STATE_DAT_READ;

endmodule