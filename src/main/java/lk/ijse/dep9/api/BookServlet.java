package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.BookDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "BookServlet", value = "/books/*",loadOnStartup = 0)
public class BookServlet extends HttpServlet2 {

    @Resource(lookup = "java:/comp/env/jdbc/dep9_lms")
    private DataSource pool;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query!=null && size!=null && page!=null){
                if (!page.matches("\\d+") || !size.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid size or page");
                }else {
                    paginatedSearchBooks(query,Integer.parseInt(size),Integer.parseInt(page),response);
                }
            } else if (query != null) {
                searchBooks(query,response);
            } else if (page != null && size != null) {
                if (!page.matches("\\d+") || !size.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid size or page");
                }else {
                    paginatedLoadBooks(Integer.parseInt(size),Integer.parseInt(page),response);
                }
            }else {
                loadAllBooks(response);
            }
        }else {
            Matcher matcher = Pattern.compile("^/(\\d{13})/?$").matcher(request.getPathInfo());
            if (matcher.matches()){
                getBookDetails(matcher.group(1),response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid URL");
            }
        }
    }

    private void loadAllBooks(HttpServletResponse response) throws IOException {

        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM book");

            ArrayList<BookDTO> books = new ArrayList<>();
            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BookDTO(isbn,title,author,copies));
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"can,t load all books");
        }

    }

    private void paginatedLoadBooks(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT COUNT(isbn) AS count FROM book");
            rst.next();
            int totalBooks = rst.getInt("count");
            response.setIntHeader("X-Total-Count",totalBooks);

            PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM book LIMIT ? OFFSET ?");
            stm2.setInt(1,size);
            stm2.setInt(2,(page-1)*size);

            rst = stm2.executeQuery();
            ArrayList<BookDTO> paginatedBooks = new ArrayList<>();

            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                paginatedBooks.add(new BookDTO(isbn,title,author,copies));
            }
            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(paginatedBooks,response.getWriter());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void searchBooks(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies = ?");
            query = "%"+query+"%";

            for (int i = 1; i < 5; i++) {
                stm.setString(i,query);
            }

            ResultSet rst = stm.executeQuery();
            ArrayList<BookDTO> books = new ArrayList<>();
            while (rst.next()) {
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BookDTO(isbn,title,author,copies));
            }
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"can't fetch search books");
        }
    }

    private void paginatedSearchBooks(String query, int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement countStm = connection.prepareStatement("SELECT COUNT(isbn) AS count FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies = ?");
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? OR copies = ? LIMIT ? OFFSET ?");

            query ='%'+query+'%';
            for (int i = 1; i < 5; i++) {
                countStm.setString(i,query);
                stm.setString(i,query);
            }
            stm.setInt(5,size);
            stm.setInt(6,(page-1)*size);

            ResultSet rst = countStm.executeQuery();
            rst.next();
            response.setIntHeader("X-Total-Count",rst.getInt("count"));

            ArrayList<BookDTO> books = new ArrayList<>();

            ResultSet rst2 = stm.executeQuery();
            while (rst2.next()){
                String isbn = rst2.getString("isbn");
                String title = rst2.getString("title");
                String author = rst2.getString("author");
                int copies = rst2.getInt("copies");

                BookDTO book = new BookDTO(isbn, title, author, copies);
                books.add(book);
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void getBookDetails(String isbn, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn=?");
            stm.setString(1,isbn);
            ResultSet rst = stm.executeQuery();

            if (rst.next()){
                String isbn1 = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                response.setHeader("Access-Control-Allow-Origin","*");
                response.setContentType("application/json");
                JsonbBuilder.create().toJson(new BookDTO(isbn1,title,author,copies),response.getWriter());

            }else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid book isbn");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Fail to fetch books");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            try {
                if (request.getContentType() == null || !request.getContentType().startsWith("application/json")){
                    throw new JsonbException("Invalid JSON");
                }

                BookDTO book = JsonbBuilder.create().fromJson(request.getReader(), BookDTO.class);
                if (book.getIsbn() == null || !book.getIsbn().matches("^\\d{13}$")){
                    throw new JsonbException("ISBN is empty or Invalid");
                } else if (book.getTitle() == null || !book.getTitle().matches("^[A-Za-z., ]+$")) {
                    throw new JsonbException("Title is empty or Invalid");
                } else if (book.getAuthor() == null || !book.getAuthor().matches("^[A-Za-z. ]+$")) {
                    throw new JsonbException("Author is empty or Invalid");
                }

                try (Connection connection = pool.getConnection()) {

                    PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM book WHERE isbn=?");
                    stm2.setString(1,book.getIsbn());
                    ResultSet rst = stm2.executeQuery();
                    if (rst.next()){
                        response.sendError(HttpServletResponse.SC_CONFLICT,"Duplicate ISBN Not allowed");
                    }


                    PreparedStatement stm = connection.prepareStatement("INSERT INTO book (isbn,title,author,copies) VALUES (?,?,?,?)");
                    stm.setString(1, book.getIsbn());
                    stm.setString(2, book.getTitle());
                    stm.setString(3, book.getAuthor());
                    stm.setInt(4,book.getCopies());


                    int affectedRows = stm.executeUpdate();
                    if (affectedRows == 1){
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setHeader("Access-Control-Allow-Origin","*");
                        response.setContentType("application/json");
                        JsonbBuilder.create().toJson(book,response.getWriter());
                    }else {
                        throw new SQLException("Something Went Wrong");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } catch (JsonbException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
            }
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()== null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("^/(\\d{13}+)/?$").matcher(request.getPathInfo());
        if (matcher.matches()){
            updateBook(matcher.group(1),request,response);
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, HEAD, OPTIONS, PUT");

        String headers = req.getHeader("Access-Control-Request-Headers");
        if (headers != null){
            resp.setHeader("Access-Control-Allow-Headers", headers);
            resp.setHeader("Access-Control-Expose-Headers", headers);
        }
    }

    private void updateBook(String isbn, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getContentType()==null ||!request.getContentType().startsWith("application/json")){
                throw new JsonbException("Invalid JSON");
            }

            BookDTO book = JsonbBuilder.create().fromJson(request.getReader(), BookDTO.class);
            if (book.getIsbn() == null || !isbn.equalsIgnoreCase(book.getIsbn())){
                throw new JsonbException("ISBN is empty or invalid");
            } else if (book.getTitle() == null || !book.getTitle().matches("^[A-Za-z., ]+$")) {
                throw new JsonbException("Title is empty or Invalid");
            } else if (book.getAuthor() == null || !book.getAuthor().matches("^[A-Za-z., ]+$")) {
                throw new JsonbException("Author is empty or Invalid");
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("UPDATE book SET title=?, author=?, copies=? WHERE isbn=?");
                stm.setString(1,book.getTitle());
                stm.setString(2,book.getAuthor());
                stm.setInt(3,book.getCopies());
                stm.setString(4,book.getIsbn());

                int affectedRow = stm.executeUpdate();
                if (affectedRow==1){
                    response.setHeader("Access-Control-Allow-Origin","*");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,"Book doesn't exist");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Fail to update book");
            }

        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
        }
    }
}
